package pl.codelobby.execution.service;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MedianTest {
 @Test void medianOfFiveIgnoresOutlier(){assertThat(ExecutionService.median(List.of(1000L,3L,1L,4L,2L))).isEqualTo(3);}
 @Test void emptyHasNoMeasurement(){assertThat(ExecutionService.median(List.of())).isNull();}
 @Test void evenCountRoundsAverage(){assertThat(ExecutionService.median(List.of(4L,1L))).isEqualTo(3);}
 @Test void doesNotChangeInput(){var input=new java.util.ArrayList<>(List.of(3L,1L,2L));ExecutionService.median(input);assertThat(input).containsExactly(3L,1L,2L);}
}
