package bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zelva.concurrent.AtomicTransferArray;

import java.util.concurrent.ThreadLocalRandom;

//  2162788,258 ± 50642,165  ops/s
//  3824814,499 ± 35663,373  ops/s
@State(Scope.Benchmark)
public class AtomicTransferArrayBench {
    private AtomicTransferArray<Integer> myArray;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AtomicTransferArrayBench.class.getSimpleName())
                .measurementIterations(4)
                .forks(1)
                .threads(4)
                .threadGroups(4)
                .syncIterations(false)
                .build();
        new Runner(opt).run();
    }

    @Setup
    public void prepare() {
        myArray = new AtomicTransferArray<>(2);
    }

    @Benchmark
    public Integer growAtomicArray() {
        int i = ThreadLocalRandom.current().nextInt(1, 8);
        myArray.resize(i);
        myArray.set(0, i);
        return i;
    }
}
