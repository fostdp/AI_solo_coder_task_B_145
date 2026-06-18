package com.fishwash.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ThreadPoolConfig — 独立线程池配置")
class ThreadPoolConfigTest {

    private final ThreadPoolConfig config = new ThreadPoolConfig();

    @Nested
    @DisplayName("femComputationExecutor — FEM有限元计算线程池")
    class FemExecutor {

        @Test
        @DisplayName("线程池名称前缀正确")
        void threadNamePrefix() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.femComputationExecutor();
            assertEquals("fem-compute-", exec.getThreadNamePrefix());
        }

        @Test
        @DisplayName("CPU密集：核心数=CPU/2，上限合理")
        void corePoolSize() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.femComputationExecutor();
            int cpu = Runtime.getRuntime().availableProcessors();
            int expectedCore = Math.max(2, cpu / 2);
            assertEquals(expectedCore, exec.getCorePoolSize(),
                    "核心池=CPU/2");
            assertTrue(exec.getMaxPoolSize() >= exec.getCorePoolSize());
        }

        @Test
        @DisplayName("拒绝策略=CallerRunsPolicy（避免任务丢失）")
        void rejectionPolicy() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.femComputationExecutor();
            exec.initialize();
            ThreadPoolExecutor tpe = exec.getThreadPoolExecutor();
            assertTrue(tpe.getRejectedExecutionHandler() instanceof
                            ThreadPoolExecutor.CallerRunsPolicy,
                    "CallerRunsPolicy：饱和时调用方线程执行");
        }

        @Test
        @DisplayName("队列容量≥100，优雅关闭等待")
        void queueAndShutdown() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.femComputationExecutor();
            assertTrue(exec.getQueueCapacity() >= 100, "队列≥100");
            assertTrue(exec.isWaitForTasksToCompleteOnShutdown(),
                    "优雅关闭：等任务完成");
            assertTrue(exec.getAwaitTerminationSeconds() > 0,
                    "等待终止秒数>0");
        }
    }

    @Nested
    @DisplayName("frictionAnalysisExecutor — 摩擦分析线程池")
    class FrictionExecutor {

        @Test
        @DisplayName("线程池名称前缀正确")
        void threadNamePrefix() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.frictionAnalysisExecutor();
            assertEquals("friction-analysis-", exec.getThreadNamePrefix());
        }

        @Test
        @DisplayName("IO+CPU混合：核心池≥2，最大≥核心")
        void poolSize() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.frictionAnalysisExecutor();
            assertTrue(exec.getCorePoolSize() >= 2);
            assertTrue(exec.getMaxPoolSize() >= exec.getCorePoolSize());
        }

        @Test
        @DisplayName("拒绝策略=CallerRunsPolicy")
        void rejectionPolicy() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.frictionAnalysisExecutor();
            exec.initialize();
            assertTrue(exec.getThreadPoolExecutor().getRejectedExecutionHandler()
                    instanceof ThreadPoolExecutor.CallerRunsPolicy);
        }
    }

    @Nested
    @DisplayName("vrInteractionExecutor — VR交互线程池")
    class VRExecutor {

        @Test
        @DisplayName("线程池名称前缀正确")
        void threadNamePrefix() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.vrInteractionExecutor();
            assertEquals("vr-interaction-", exec.getThreadNamePrefix());
        }

        @Test
        @DisplayName("低延迟高并发：核心池≥4，响应灵敏")
        void corePoolSize() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.vrInteractionExecutor();
            assertTrue(exec.getCorePoolSize() >= 4, "VR交互核心池≥4，实际=" + exec.getCorePoolSize());
            assertTrue(exec.getMaxPoolSize() >= 8);
        }

        @Test
        @DisplayName("拒绝策略=CallerRunsPolicy")
        void rejectionPolicy() {
            ThreadPoolTaskExecutor exec = (ThreadPoolTaskExecutor) config.vrInteractionExecutor();
            exec.initialize();
            assertTrue(exec.getThreadPoolExecutor().getRejectedExecutionHandler()
                    instanceof ThreadPoolExecutor.CallerRunsPolicy);
        }
    }

    @Nested
    @DisplayName("3个线程池独立隔离")
    class Isolation {

        @Test
        @DisplayName("3个Bean名称、前缀均不同，互不干扰")
        void threeDistinctExecutors() {
            ThreadPoolTaskExecutor fem = (ThreadPoolTaskExecutor) config.femComputationExecutor();
            ThreadPoolTaskExecutor fric = (ThreadPoolTaskExecutor) config.frictionAnalysisExecutor();
            ThreadPoolTaskExecutor vr = (ThreadPoolTaskExecutor) config.vrInteractionExecutor();

            assertNotEquals(fem.getThreadNamePrefix(), fric.getThreadNamePrefix());
            assertNotEquals(fric.getThreadNamePrefix(), vr.getThreadNamePrefix());
            assertNotEquals(fem.getThreadNamePrefix(), vr.getThreadNamePrefix());

            assertNotSame(fem, fric);
            assertNotSame(fem, vr);
            assertNotSame(fric, vr);
        }
    }
}
