require "rubygems/version"
require "json"
require "fileutils"
require "tmpdir"
require "shellwords"

class IsolateJob < ApplicationJob
  retry_on RuntimeError, wait: 0.1.seconds, attempts: 100

  queue_as ENV["JUDGE0_VERSION"].to_sym

  MIN_VERSION_WITHOUT_CG_TIMING = Gem::Version.new("2.0.0")
  CG_TIMING_SUPPORTED = begin
    version_line = `isolate --version 2>/dev/null`.lines.first.to_s
    version_match = version_line[/\d+\.\d+(?:\.\d+)?/]
    if version_match
      Gem::Version.new(version_match) < MIN_VERSION_WITHOUT_CG_TIMING
    else
      true
    end
  rescue StandardError
    true
  end

  OUTPUT_LIMIT_BYTES = 65_536
  STDIN_FILE_NAME = "stdin.txt"
  STDOUT_FILE_NAME = "stdout.txt"
  STDERR_FILE_NAME = "stderr.txt"
  METADATA_FILE_NAME = "metadata.txt"
  ADDITIONAL_FILES_ARCHIVE_FILE_NAME = "additional_files.zip"
  SUITE_REQUEST_PREFIX = "CODELOBBY_TEST_SUITE_V1:"
  SUITE_RESULT_PREFIX = "CODELOBBY_TEST_SUITE_RESULT_V1:"
  SUITE_EXPECTED_OUTPUT = "CODELOBBY_INTERNAL_SUITE_V1"

  attr_reader :submission, :cgroups,
              :box_id, :workdir, :boxdir, :tmpdir,
              :source_file, :stdin_file, :stdout_file,
              :stderr_file, :metadata_file, :additional_files_archive_file

  def perform(submission_id)
    @phase_timings = Hash.new(0.0)
    @job_started = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    @submission = Submission.find(submission_id)
    submission.update(status: Status.process, started_at: DateTime.now, execution_host: ENV["HOSTNAME"])
    @suite_inputs = parse_suite_inputs(submission.stdin) if submission.expected_output == SUITE_EXPECTED_OUTPUT
    submission.expected_output = nil if @suite_inputs

    warmup_runs = submission.number_of_runs == 7 ? 2 : 0
    time_samples = []
    memory_samples = []

    initialize_workdir
    if compile == :failure
      cleanup
      return
    end
    if @suite_inputs
      run_suite
      return
    end
    reset_sandbox_after_compile if @compiled_submission

    submission.number_of_runs.times do |run_index|
      run
      verify

      if run_index >= warmup_runs
        time_samples << submission.time.to_f
        memory_samples << submission.memory.to_f
      end

      if submission.status != Status.ac
        cleanup
        break
      elsif run_index + 1 < submission.number_of_runs
        # Recreate the Isolate box to reset per-run peak-memory accounting,
        # while retaining the one compiled binary from the preparation phase.
        reset_sandbox_after_compile if @compiled_submission
      else
        cleanup
      end
    end

    unless time_samples.empty?
      submission.time = median(time_samples)
      submission.memory = memory_samples.max
      submission.save
    end

  rescue Exception => e
    raise e.message unless submission
    submission.update(message: e.message, status: Status.boxerr, finished_at: DateTime.now)
    cleanup(raise_exception = false)
  ensure
    if @job_started
      puts "CODELOBBY_TIMING " + JSON.generate({ submission_id: submission_id,
        total_ms: ((Process.clock_gettime(Process::CLOCK_MONOTONIC) - @job_started) * 1000).round(2),
        phases_ms: @phase_timings.transform_values { |seconds| (seconds * 1000).round(2) } })
    end
    call_callback
  end

  private

  def parse_suite_inputs(stdin)
    return nil unless stdin.to_s.start_with?(SUITE_REQUEST_PREFIX)

    payload = JSON.parse(stdin.delete_prefix(SUITE_REQUEST_PREFIX))
    inputs = payload["inputs"]
    @suite_expected = payload["expectedOutputs"]
    @suite_benchmark_enabled = payload.fetch("benchmark", true)
    unless inputs.is_a?(Array) && inputs.length.between?(1, 50) &&
           inputs.all? { |input| input.is_a?(String) && input.bytesize <= 400_000 }
      raise "Invalid CodeLobby test suite input."
    end
    unless @suite_expected.is_a?(Array) && @suite_expected.length == inputs.length &&
           @suite_expected.all? { |output| output.is_a?(String) && output.bytesize <= 400_000 }
      raise "Invalid CodeLobby expected outputs."
    end
    if payload["batch"] == true
      @batch_case_count = inputs.length
      @suite_expected = [@suite_expected.map { |value| normalize_suite_output(value) }.join("\n") + "\n"]
      return [inputs.length.to_s + "\n" + inputs.map { |value| value.delete_suffix("\n") + "\n" }.join]
    end
    inputs
  rescue JSON::ParserError
    raise "Invalid CodeLobby test suite JSON."
  end

  def run_suite
    # Keep one pristine compiled copy outside Isolate. Never put the full test
    # suite into /box: student code must see only the current case's stdin.
    staging_dir = Dir.mktmpdir("judge0-suite-#{submission.id}-")
    staged_box = File.join(staging_dir, "box")
    unless system("sudo", "cp", "-a", boxdir, staged_box) &&
           system("sudo", "chown", "-R", "#{Process.uid}:#{Process.gid}", staged_box)
      raise "Could not stage the compiled submission for test cases."
    end

    results = @suite_inputs.each_with_index.map do |input, index|
      # Isolate accepts IDs 0..999. The single local worker reserves 800..849
      # for cases and restores every box from the same pristine compiled copy.
      reset_sandbox_after_compile(staged_box, 800 + index)
      @stdin_file = File.join(boxdir, "case_#{index + 1}.stdin")
      File.binwrite(stdin_file, input)
      run
      verify
      # This Judge0 model memoizes decoded stdout/stderr on first read. Read
      # each case's actual files instead of its cached Submission getters.
      case_stdout = bounded_text(stdout_file)
      case_stderr = bounded_text(stderr_file)
      {
        status: submission.status.name,
        stdout: case_stdout.empty? ? nil : case_stdout,
        stderr: case_stderr.empty? ? nil : case_stderr,
        time: submission.time.nil? ? nil : submission.time.to_f,
        memory: submission.memory.nil? ? nil : submission.memory.to_f,
        message: submission.message
      }
    end

    benchmark = nil
    if @suite_benchmark_enabled && results.each_with_index.all? { |result, index|
         result[:status] == Status.ac.name && !result[:message].to_s.start_with?("Memory limit exceeded", "Output limit exceeded") && normalize_suite_output(result[:stdout]) == normalize_suite_output(@suite_expected[index]) }
      samples = []
      memories = []
      benchmark = { status: "COMPLETED", warmupRuns: 2, measuredRuns: 0, measurementScope: "SUITE_SUM_OF_PROCESSES", measurementTrust: "SANDBOX" }
      if @batch_case_count
        benchmark[:measurementScope] = "FUNCTION_CALLS_ELAPSED"
        benchmark[:measurementTrust] = "IN_PROCESS_UNTRUSTED"
      end
      original_cpu_limit = submission.cpu_time_limit
      original_wall_limit = submission.wall_time_limit
      # Seven rounds in one process need a distinct, bounded aggregate budget.
      if @batch_case_count
        submission.cpu_time_limit = [original_cpu_limit * 7 + 1, 30].min
        submission.wall_time_limit = [original_wall_limit * 7 + 2, 60].min
      end
      (@batch_case_count ? 1 : 7).times do |round|
        total_time = 0.0
        round_memory = 0.0
        valid = true
        @suite_inputs.each_with_index do |input, index|
          reset_sandbox_after_compile(staged_box, 800 + index)
          @stdin_file = File.join(boxdir, "case_#{index + 1}.stdin")
          File.binwrite(stdin_file, @batch_case_count ? "CODELOBBY_BENCH_V1\n" + input : input)
          run
          verify
          unless submission.status == Status.ac && !submission.message.to_s.start_with?("Memory limit exceeded", "Output limit exceeded") &&
                 normalize_suite_output(File.read(stdout_file)) == normalize_suite_output(@suite_expected[index])
            valid = false
            break
          end
          total_time += submission.time.to_f
          round_memory = [round_memory, submission.memory.to_f].max
        end
        unless valid
          benchmark[:status] = "FAILED"
          break
        end
        if @batch_case_count
          markers = File.read(stderr_file).lines.grep(/\ACODELOBBY_TIMES_V1:/)
          values = markers.length == 1 ? JSON.parse(markers.first.delete_prefix("CODELOBBY_TIMES_V1:")) : nil
          unless values.is_a?(Array) && values.length == 5 && values.all? { |v| v.is_a?(Numeric) && v.finite? && v >= 0 } && values.sum <= total_time * 10 + 1
            benchmark[:status] = "FAILED"
            break
          end
          samples = values
          memories = [round_memory]
          benchmark[:measuredRuns] = 5
          benchmark[:measurementScope] = "FUNCTION_CALLS_ELAPSED"
          benchmark[:measurementTrust] = "IN_PROCESS_UNTRUSTED"
          benchmark[:sandboxCpuTime] = total_time
        elsif round >= 2
          samples << total_time
          memories << round_memory
          benchmark[:measuredRuns] = samples.length
        end
      end
      submission.cpu_time_limit = original_cpu_limit
      submission.wall_time_limit = original_wall_limit
      if benchmark[:status] == "COMPLETED"
        benchmark[:medianTime] = median(samples)
        benchmark[:peakMemory] = memories.max
      end
    end

    if @batch_case_count
      process_result = results.first
      lines = process_result[:stdout].to_s.lines.map { |line| line.delete_suffix("\n") }
      progress = process_result[:stderr].to_s.lines.filter_map { |line|
        match = line.match(/\ACODELOBBY_CASE_V1:([0-9]+)\s*\z/)
        match && match[1].to_i
      }.last
      if !progress.nil? && progress.between?(1, @batch_case_count) && (process_result[:status] != Status.ac.name || process_result[:message] == "Memory limit exceeded")
        process_result[:message] = process_result[:message].to_s + "; Execution interrupted at test #{progress}; earlier results unavailable (in-process diagnostic)"
      end
      valid_records = lines.length == @batch_case_count && lines.all? { |line| line.match?(/\A[0-9]+\z/) }
      results = Array.new(@batch_case_count) do |index|
        {
          status: process_result[:status] == Status.ac.name && !valid_records ? Status.wa.name : process_result[:status],
          stdout: valid_records ? lines[index] + "\n" : nil,
          stderr: nil,
          time: nil, memory: nil,
          message: process_result[:message]
        }
      end
    end
    submission.stdout = SUITE_RESULT_PREFIX + JSON.generate({ protocol: "codelobby-suite-v1", results: results, benchmark: benchmark })
    submission.stderr = nil
    submission.time = results.sum { |result| result[:time].to_f }
    submission.memory = results.filter_map { |result| result[:memory] }.max
    submission.exit_code = 0
    submission.exit_signal = nil
    submission.message = nil
    submission.status = Status.ac
    submission.finished_at = DateTime.now
    submission.save!
    cleanup
  ensure
    FileUtils.remove_entry(staging_dir) if staging_dir && File.exist?(staging_dir)
  end

  def median(values)
    sorted = values.sort
    middle = sorted.length / 2
    sorted.length.odd? ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0
  end

  def normalize_suite_output(value)
    value.to_s.gsub("\r\n", "\n").gsub("\r", "\n").sub(/[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+\z/, "")
  end

  def initialize_workdir(box_id_override = nil)
    @box_id = box_id_override || submission.id % 800
    @cgroups = (!submission.enable_per_process_and_thread_time_limit || !submission.enable_per_process_and_thread_memory_limit) ? "--cg" : ""
    @workdir = `isolate #{cgroups} -b #{box_id} --init`.chomp
    @boxdir = workdir + "/box"
    @tmpdir = workdir + "/tmp"
    @source_file = boxdir + "/" + (submission.language_id == 71 ? "main.py" : submission.language.source_file.to_s)
    @stdin_file = boxdir + "/" + STDIN_FILE_NAME
    @stdout_file = workdir + "/" + STDOUT_FILE_NAME
    @stderr_file = workdir + "/" + STDERR_FILE_NAME
    @metadata_file = workdir + "/" + METADATA_FILE_NAME
    @additional_files_archive_file = boxdir + "/" + ADDITIONAL_FILES_ARCHIVE_FILE_NAME

    initialize_files([stdin_file, stdout_file, stderr_file, metadata_file])

    File.open(source_file, "wb") { |f| f.write(submission.source_code) } unless submission.is_project
    File.open(stdin_file, "wb") { |f| f.write(@suite_inputs ? "" : submission.stdin) }

    extract_archive
  end

  def initialize_file(file)
    initialize_files([file])
  end

  def initialize_files(files)
    # One privileged process for all files, with paths passed as arguments.
    unless system("sudo", "sh", "-c", 'owner=$1; shift; touch -- "$@" && chown "$owner" -- "$@"',
                  "codelobby-init", "#{Process.uid}:#{Process.gid}", *files)
      raise "Could not initialize sandbox files."
    end
  end

  def extract_archive
    return unless submission.additional_files?

    File.open(additional_files_archive_file, "wb") { |f| f.write(submission.additional_files) }

    command = "isolate #{cgroups} \
    -s \
    -b #{box_id} \
    --stderr-to-stdout \
    -t 2 \
    -x 1 \
    -w 4 \
    -k #{Config::MAX_STACK_LIMIT} \
    -p#{Config::MAX_MAX_PROCESSES_AND_OR_THREADS} \
    #{cg_timing_flag_for(submission)} \
    #{submission.enable_per_process_and_thread_memory_limit ? "-m " : "--cg-mem="}#{Config::MAX_MEMORY_LIMIT} \
    -f #{Config::MAX_EXTRACT_SIZE} \
    --run \
    -- /usr/bin/unzip -n -qq #{ADDITIONAL_FILES_ARCHIVE_FILE_NAME} \
    "

    puts "[#{DateTime.now}] Extracting archive for submission #{submission.token} (#{submission.id}):"
    puts command.gsub(/\s+/, " ")
    puts

    `#{command}`

    File.delete(additional_files_archive_file)
  end

  def compile
    unless submission.is_project
      return :success unless submission.language.compile_cmd
    end

    compile_script = boxdir + "/" + "compile.sh"

    acceptable_project_compile_scripts = [compile_script, boxdir + "/" + "compile"]
    if submission.is_project
      compile_file_exists = false
      acceptable_project_compile_scripts.each do |f|
        if File.file?(f)
          compile_script = f
          compile_file_exists = true
          break
        end
      end

      unless compile_file_exists
        return :success # If compile script does not exist then this project does not need to be compiled.
      end
    else
      # gsub can be skipped if compile script is used, but is kept for additional security.
      compiler_options = submission.compiler_options.to_s.strip.encode("UTF-8", invalid: :replace).gsub(/[$&;<>|`]/, "")
      compile_command = submission.language.compile_cmd % compiler_options
      if submission.language_id == 54
        cpp_sources = Dir.children(boxdir).grep(/\A[A-Za-z_][A-Za-z0-9_]*\.cpp\z/).sort
        compile_command = (submission.language.compile_cmd % "-std=c++17 -O2").sub(/main\.cpp/, Shellwords.join(cpp_sources))
      end
      if submission.language_id == 62
        # Server validated flat source paths; do not discover class entrypoints or accept build scripts.
        java_sources = Dir.children(boxdir).grep(/\A[A-Za-z_][A-Za-z0-9_]*\.java\z/).sort
        compile_command = (submission.language.compile_cmd % "").sub(/Main\.java/, Shellwords.join(java_sources))
      end
      if submission.language_id == 62
        compile_command = compile_command.sub("/javac ", "/javac -J-Xmx64m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=1 -J-XX:CompressedClassSpaceSize=16m -J-XX:ReservedCodeCacheSize=16m ")
      end
      File.open(compile_script, "w") { |f| f.write(compile_command) }
    end

    compile_output_file = workdir + "/" + "compile_output.txt"
    initialize_file(compile_output_file)
    compile_cpu_limit = submission.language_id == 62 ? 10 : Config::MAX_CPU_TIME_LIMIT
    compile_wall_limit = submission.language_id == 62 ? 20 : Config::MAX_WALL_TIME_LIMIT
    compile_memory_limit = submission.language_id == 62 ? 512000 : Config::MAX_MEMORY_LIMIT

    command = "isolate #{cgroups} \
    -s \
    -b #{box_id} \
    -M #{metadata_file} \
    --stderr-to-stdout \
    -i /dev/null \
    -t #{compile_cpu_limit} \
    -x 0 \
    -w #{compile_wall_limit} \
    -k #{Config::MAX_STACK_LIMIT} \
    -p#{Config::MAX_MAX_PROCESSES_AND_OR_THREADS} \
    #{cg_timing_flag_for(submission)} \
    #{submission.enable_per_process_and_thread_memory_limit ? "-m " : "--cg-mem="}#{compile_memory_limit} \
    -f #{Config::MAX_MAX_FILE_SIZE} \
    -E HOME=/tmp \
    -E PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\" \
    -E LANG -E LANGUAGE -E LC_ALL -E JUDGE0_HOMEPAGE -E JUDGE0_SOURCE_CODE -E JUDGE0_MAINTAINER -E JUDGE0_VERSION \
    -d /etc:noexec \
    --run \
    -- /bin/bash $(basename #{compile_script}) > #{compile_output_file} \
    "

    puts "[#{DateTime.now}] Compiling submission #{submission.token} (#{submission.id}):"
    puts command.gsub(/\s+/, " ")
    puts

    `#{command}`
    process_status = $?

    compile_output = bounded_text(compile_output_file)
    compile_output = nil if compile_output.empty?
    submission.compile_output = compile_output

    metadata = get_metadata

    reset_metadata_file

    files_to_remove = [compile_output_file]
    files_to_remove << compile_script unless submission.is_project
    files_to_remove.each do |f|
      `sudo rm -rf #{f}`
    end

    if process_status.success?
      @compiled_submission = true
      return :success
    end

    if metadata[:status] == "TO"
      submission.compile_output = "Compilation time limit exceeded."
    end

    submission.finished_at = DateTime.now
    submission.time = nil
    submission.wall_time = nil
    submission.memory = nil
    submission.stdout = nil
    submission.stderr = nil
    submission.exit_code = nil
    submission.exit_signal = nil
    submission.message = nil
    submission.status = Status.ce
    submission.save

    return :failure
  end

  # Isolate keeps cgroup memory peak/cache accounting for repeated runs in the
  # same box. Judge0 compiles and runs in one box, so the reported `cg-mem`
  # otherwise includes compiler memory. Start a fresh box for execution while
  # carrying the compiled submission files across, as Piston does.
  def reset_sandbox_after_compile(staged_box = nil, new_box_id = nil)
    unless staged_box
      staging_dir = Dir.mktmpdir("judge0-compiled-#{submission.id}-")
      staged_box = File.join(staging_dir, "box")
      unless system("sudo", "cp", "-a", boxdir, staged_box)
        raise "Could not stage compiled submission for a fresh execution sandbox."
      end
      unless system("sudo", "chown", "-R", "#{Process.uid}:#{Process.gid}", staged_box)
        raise "Could not prepare compiled submission for a fresh execution sandbox."
      end
    end

    cleanup
    initialize_workdir(new_box_id)
    restore_compiled_box(staged_box)
  ensure
    FileUtils.remove_entry(staging_dir) if staging_dir && File.exist?(staging_dir)
  end

  def restore_compiled_box(staged_box)
    entries = Dir.children(boxdir).map { |entry| File.join(boxdir, entry) }
    # Keep the directory created by `isolate --init`; replacing its inode can
    # leave Isolate's bind mount pointing at the old box contents.
    unless system("sudo", "sh", "-c",
                  'owner=$1; source=$2; target=$3; shift 3; rm -rf -- "$@" && cp -a -- "$source" "$target" && chown -R "$owner" -- "$target"',
                  "codelobby-restore", "#{Process.uid}:#{Process.gid}", File.join(staged_box, "."), boxdir, *entries)
      raise "Could not prepare restored files in the execution sandbox."
    end
  end

  def run
    run_script = boxdir + "/" + "run.sh"

    acceptable_project_run_scripts = [run_script, boxdir + "/" + "run"]
    acceptable_project_run_scripts.each do |f|
      if File.file?(f)
        run_script = f
        break
      end
    end

    unless submission.is_project
      # gsub is mandatory!
      command_line_arguments = submission.command_line_arguments.to_s.strip.encode("UTF-8", invalid: :replace).gsub(/[$&;<>|`]/, "")
      run_command = submission.language.run_cmd
      run_command = run_command.sub(/script\.py/, "main.py") if submission.language_id == 71
      if submission.language_id == 62
        # Heap is a subset of the enforced process/cgroup memory budget.
        heap_mb = [16, [64, (submission.memory_limit.to_i / 1000) / 2].min].max
        run_command = run_command.sub("/java ", "/java -Xms16m -Xmx#{heap_mb}m -Xss256k -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:MaxMetaspaceSize=32m -XX:CompressedClassSpaceSize=16m -XX:ReservedCodeCacheSize=16m ")
      end
      File.open(run_script, "w") { |f| f.write("#{run_command} #{command_line_arguments}") }
    end

    command = "isolate #{cgroups} \
    -s \
    -b #{box_id} \
    -M #{metadata_file} \
    -i /box/#{File.basename(stdin_file)} \
    #{submission.redirect_stderr_to_stdout ? "--stderr-to-stdout" : ""} \
    #{submission.enable_network ? "--share-net" : ""} \
    -t #{submission.cpu_time_limit} \
    -x #{submission.cpu_extra_time} \
    -w #{submission.wall_time_limit} \
    -k #{submission.stack_limit} \
    -p#{submission.max_processes_and_or_threads} \
    #{cg_timing_flag_for(submission)} \
    #{submission.enable_per_process_and_thread_memory_limit ? "-m " : "--cg-mem="}#{submission.memory_limit} \
    -f #{submission.max_file_size} \
    -E HOME=/tmp \
    -E PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\" \
    -E LANG -E LANGUAGE -E LC_ALL -E JUDGE0_HOMEPAGE -E JUDGE0_SOURCE_CODE -E JUDGE0_MAINTAINER -E JUDGE0_VERSION \
    -d /etc:noexec \
    --run \
    -- /bin/bash $(basename #{run_script}) \
    "

    puts "[#{DateTime.now}] Running submission #{submission.token} (#{submission.id}):"
    puts command.gsub(/\s+/, " ")
    puts

    capture_program_output(command)

    `sudo rm #{run_script}` unless submission.is_project
  end

  # Independent stdout/stderr guards. Closing an overflowing pipe prevents further
  # storage; a program ignoring EPIPE is still bounded by Isolate CPU/wall limits.
  # This does not lower the file-size allowance for the student's temporary files.
  def capture_program_output(command)
    @output_exceeded = false
    out_reader, out_writer = IO.pipe
    err_reader, err_writer = IO.pipe
    readers = []
    pid = Process.spawn(command, out: out_writer, err: err_writer)
    out_writer.close
    err_writer.close
    [[out_reader, stdout_file], [err_reader, stderr_file]].each do |reader, path|
      readers << Thread.new do
        begin
          written = 0
          File.open(path, "wb") do |file|
            loop do
              chunk = reader.readpartial(4096)
              remaining = OUTPUT_LIMIT_BYTES - written
              file.write(chunk.byteslice(0, remaining)) if remaining > 0
              written += [chunk.bytesize, remaining].min
              if chunk.bytesize > remaining
                @output_exceeded = true
                break
              end
            end
          end
        rescue EOFError, IOError
          # EOF (or close while cleaning up) ends the bounded stream.
        ensure
          reader.close unless reader.closed?
        end
      end
    end
    Process.wait(pid)
    readers.each { |thread| thread.join(2) }
  ensure
    [out_reader, out_writer, err_reader, err_writer].compact.each { |io| io.close unless io.closed? }
    readers.each(&:join) if readers
  end

  def verify
    submission.finished_at = DateTime.now

    metadata = get_metadata

    program_stdout = bounded_text(stdout_file)
    program_stdout = nil if program_stdout.empty?

    program_stderr = bounded_text(stderr_file)
    program_stderr = nil if program_stderr.empty?

    submission.time = metadata[:time]
    submission.wall_time = metadata[:"time-wall"]
    submission.memory = (cgroups.present? ? metadata[:"cg-mem"] : metadata[:"max-rss"])
    submission.stdout = program_stdout
    submission.stderr = program_stderr
    submission.exit_code = metadata[:exitcode].try(:to_i) || 0
    submission.exit_signal = metadata[:exitsig].try(:to_i)
    submission.message = metadata[:message]
    submission.status = determine_status(metadata[:status], submission.exit_signal)
    submission.message = "Memory limit exceeded" if metadata.key?(:"cg-oom-killed")
    if @output_exceeded
      submission.status = Status.nzec
      submission.message = "Output limit exceeded"
    end

    # After adding support for compiler_options and command_line_arguments
    # status "Exec Format Error" will no longer occur because compile and run
    # is done inside a dynamically created bash script, thus isolate doesn't call
    # execve directily on submission.language.compile_cmd or submission.langauge.run_cmd.
    # Consequence of running compile and run through bash script is that when
    # target binary is not found then submission gets status "Runtime Error (NZEC)".
    #
    # I think this is for now O.K. behaviour, but I will leave this if block
    # here until I am 100% sure that "Exec Format Error" can be deprecated.
    if submission.status == Status.boxerr &&
       (
         submission.message.to_s.match(/^execve\(.+\): Exec format error$/) ||
         submission.message.to_s.match(/^execve\(.+\): No such file or directory$/) ||
         submission.message.to_s.match(/^execve\(.+\): Permission denied$/)
       )
       submission.status = Status.exeerr
    end
  end

  def bounded_text(path)
    (File.binread(path, OUTPUT_LIMIT_BYTES) || "").force_encoding("UTF-8").scrub.byteslice(0, OUTPUT_LIMIT_BYTES).force_encoding("UTF-8").scrub("")
  end

  def cleanup(raise_exception = true)
    # Preserve the existing cleanup, including dotfiles, without launching
    # a separate shell and sudo process for each metadata file.
    cleaned = system("sudo", "sh", "-c",
           'owner=$1; box=$2; tmp=$3; shift 3; chown -R "$owner" -- "$box" && find "$box" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && { if [ -d "$tmp" ]; then find "$tmp" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +; fi; } && rm -f -- "$@"',
           "codelobby-cleanup", "#{Process.uid}:#{Process.gid}", boxdir, tmpdir,
           stdin_file, stdout_file, stderr_file, metadata_file)
    raise "Could not clear sandbox files." if raise_exception && !cleaned
    `isolate #{cgroups} -b #{box_id} --cleanup`
    raise "Cleanup of sandbox #{box_id} failed." if raise_exception && Dir.exists?(workdir)
  end

  def reset_metadata_file
    `sudo rm -rf #{metadata_file}`
    initialize_file(metadata_file)
  end

  def fix_permissions
    `sudo chown -R $(whoami): #{boxdir}`
  end

  def call_callback
    return unless submission.callback_url.present?

    serialized_submission = ActiveModelSerializers::SerializableResource.new(
      submission,
      {
        serializer: SubmissionSerializer,
        base64_encoded: true,
        fields: SubmissionSerializer.default_fields
      }
    ).to_json

    Config::CALLBACKS_MAX_TRIES.times do
      begin
        response = HTTParty.put(
          submission.callback_url,
          body: serialized_submission,
          headers: {
            "Content-Type" => "application/json"
          },
          timeout: Config::CALLBACKS_TIMEOUT
        )
        break
      rescue Exception => e
      end
    end
  rescue Exception => e
  end

  def get_metadata
    metadata = File.read(metadata_file).split("\n").collect do |e|
      { e.split(":").first.to_sym => e.split(":")[1..-1].join(":") }
    end.reduce({}, :merge)
    return metadata
  end

  def determine_status(status, exit_signal)
    if status == "TO"
      return Status.tle
    elsif status == "SG"
      return Status.find_runtime_error_by_status_code(exit_signal)
    elsif status == "RE"
      return Status.nzec
    elsif status == "XX"
      return Status.boxerr
    elsif @suite_inputs || submission.expected_output.nil? || strip(submission.expected_output) == strip(submission.stdout)
      return Status.ac
    else
      return Status.wa
    end
  end

  def strip(text)
    return nil unless text
    text.split("\n").collect(&:rstrip).join("\n").rstrip
  rescue ArgumentError
    return text
  end

  def cg_timing_flag_for(submission)
    return "" unless cgroups.present?
    return "" unless CG_TIMING_SUPPORTED

    if submission.enable_per_process_and_thread_time_limit
      "--no-cg-timing"
    else
      "--cg-timing"
    end
  end

  # Host-side diagnostics only: never add preparation time to student runtime.
  module PhaseTiming
    %i[compile reset_sandbox_after_compile run verify cleanup initialize_workdir restore_compiled_box].each do |name|
      define_method(name) do |*args|
        started = Process.clock_gettime(Process::CLOCK_MONOTONIC)
        super(*args)
      ensure
        @phase_timings[name] += Process.clock_gettime(Process::CLOCK_MONOTONIC) - started if @phase_timings
      end
    end
  end
  prepend PhaseTiming
end
