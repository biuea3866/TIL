package com.biuea.practice.parallel;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Java Parallel Stream의 동작 원리와 주의사항을 실습하는 예제
 * 
 * 주요 내용:
 * 1. Parallel Stream은 내부적으로 ForkJoinPool.commonPool()을 사용한다.
 * 2. 모든 병렬 스트림은 이 공용 풀을 공유하므로, 한 곳에서 블로킹 작업이 발생하면 전체에 영향을 줄 수 있다.
 * 3. 별도의 ForkJoinPool을 사용하여 작업을 격리할 수 있다.
 */
public class ParallelStreamExample {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        List<Long> numbers = LongStream.rangeClosed(1, 1_000_000)
                .boxed()
                .collect(Collectors.toList());

        // 1. 일반적인 Parallel Stream 사용 (Common Pool 사용)
        long startTime = System.currentTimeMillis();
        long sum = numbers.parallelStream()
                .mapToLong(Long::longValue)
                .sum();
        System.out.println("Common Pool Sum: " + sum + " (Time: " + (System.currentTimeMillis() - startTime) + "ms)");

        // 2. Custom ForkJoinPool을 사용한 격리
        // Common Pool의 스레드를 점유하지 않고 별도의 풀에서 실행하여 영향을 최소화함
        ForkJoinPool customPool = new ForkJoinPool(4);
        try {
            startTime = System.currentTimeMillis();
            long customSum = customPool.submit(() ->
                    numbers.parallelStream()
                            .mapToLong(Long::longValue)
                            .sum()
            ).get();
            System.out.println("Custom Pool Sum: " + customSum + " (Time: " + (System.currentTimeMillis() - startTime) + "ms)");
        } finally {
            customPool.shutdown();
        }
    }
}
