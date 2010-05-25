package com.browsermob.agent;

import com.thoughtworks.selenium.DefaultSelenium;
import com.thoughtworks.selenium.Selenium;
import org.junit.runner.Description;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerScheduler;
import org.junit.runners.model.Statement;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Patrick Lightbody (patrick@browsermob.com)
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 */
public class ConcurrentSauceRunner extends BlockJUnit4ClassRunner {
    private File rootDir;
    private SauceConfig sauceConfig;
    private String SERVER = System.getProperty("selenium.server");
    private String APP = System.getProperty("selenium.application");
    private String BROWSER = System.getProperty("selenium.browser");

    public ConcurrentSauceRunner(final Class<?> klass) throws InitializationError {
        super(klass);

        sauceConfig = getAnnotation(klass, SauceConfig.class);
        if (sauceConfig == null) {
            throw new IllegalStateException("@SauceConfig must be specified");
        }

        Artifacts artifacts = klass.getAnnotation(Artifacts.class);
        if (artifacts != null) {
            rootDir = new File(artifacts.dir());
        } else {
            rootDir = new File("target/surefire-reports");
        }
        rootDir.mkdirs();

        final Concurrent concurrent = klass.getAnnotation(Concurrent.class);

        setScheduler(new RunnerScheduler() {
            ExecutorService executorService = Executors.newFixedThreadPool(
                    concurrent != null ? concurrent.threads() : 5,
                    new NamedThreadFactory(klass.getSimpleName()));
            CompletionService<Void> completionService = new ExecutorCompletionService<Void>(executorService);
            Queue<Future<Void>> tasks = new LinkedList<Future<Void>>();

            @Override
            public void schedule(Runnable childStatement) {
                tasks.offer(completionService.submit(childStatement, null));
            }

            @Override
            public void finished() {
                try {
                    while (!tasks.isEmpty()) {
                        tasks.remove(completionService.take());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    while (!tasks.isEmpty()) {
                        tasks.poll().cancel(true);
                    }
                    executorService.shutdownNow();
                }
            }
        });
    }

    private <T> T getAnnotation(Class klass, Class<T> annotation) {
        Annotation a = klass.getAnnotation(annotation);
        if (a != null) {
            return (T) a;
        } else {
            Class sup = klass.getSuperclass();
            if (!sup.equals(Object.class)) {
                return getAnnotation(sup, annotation);
            }
        }

        return null;
    }

    @Override
    protected void validatePublicVoidNoArgMethods(Class<? extends Annotation> annotation, boolean isStatic, List<Throwable> errors) {

    }

    protected Statement methodInvoker(final FrameworkMethod method, final Object test) {
        if (SERVER == null) {
            SERVER = "localhost";
        }

        final Description description = describeChild(method);

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String jobName = description.getTestClass().getSimpleName() + "-" + description.getMethodName();

                if (BROWSER == null) {
                    BROWSER = "*firefox3";
                } else if (BROWSER.split(":").length == 3) {
                    String[] platform = BROWSER.split(":");
                    jobName = description.getMethodName() + " [" + platform[1] + " " + platform[2].charAt(0) + "]";
                    BROWSER = "{\"username\":\"" + sauceConfig.username() + "\"," +
                            "\"access-key\": \"" + sauceConfig.accessKey() + "\"," +
                            "\"os\":\"" + platform[0] + "\",\"browser\": \"" + platform[1] + "\"," +
                            "\"browser-version\":\"" + platform[2] + "\"," +
                            "\"job-name\":\"" + jobName + "\"}";
                }

                if (APP == null) {
                    APP = "http://localhost:9000";
                }

                Selenium selenium = null;
                try {
                    selenium = new DefaultSelenium(SERVER, 4444, BROWSER, APP);
                    selenium.start();

                    SeleniumTestContext.register(selenium, jobName, rootDir, BROWSER, APP);

                    method.invokeExplosively(test);
                } catch (Throwable throwable) {
                    SeleniumTestContext.takeScreenshot("FAILURE");
                    throw throwable;
                } finally {
                    SeleniumTestContext.reset();

                    if (selenium != null) {
                        selenium.stop();
                    }
                }
            }
        };
    }

    static final class NamedThreadFactory implements ThreadFactory {
        static final AtomicInteger poolNumber = new AtomicInteger(1);
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final ThreadGroup group;

        NamedThreadFactory(String poolName) {
            group = new ThreadGroup(poolName + "-" + poolNumber.getAndIncrement());
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(group, r, group.getName() + "-thread-" + threadNumber.getAndIncrement(), 0);
        }
    }
}