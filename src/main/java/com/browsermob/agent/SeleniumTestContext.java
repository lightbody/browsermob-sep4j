package com.browsermob.agent;

import com.thoughtworks.selenium.Selenium;
import org.apache.commons.codec.binary.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SeleniumTestContext {
    private static final ThreadLocal<Context> threadLocal = new ThreadLocal<Context>() {
        @Override
        protected Context initialValue() {
            return new Context();
        }
    };

    public static void register(Selenium selenium, String description, File root, String browser, String app) {
        threadLocal.set(new Context(selenium, description, root, browser, app));
    }

    public static void reset() {
        threadLocal.remove();
    }

    public static Selenium selenium() {
        return threadLocal.get().selenium;
    }

    public static String description() {
        return threadLocal.get().description;
    }

    public static File root() {
        return threadLocal.get().root;
    }

    public static String browser() {
        return threadLocal.get().browser;
    }

    public static String app() {
        return threadLocal.get().app;
    }

    public static void takeScreenshot(String id) throws IOException {
        String screen = selenium().captureScreenshotToString();
        File dir = new File(root(), description());
        dir.mkdirs();
        FileOutputStream fos = new FileOutputStream(new File(dir, id + ".png"));
        fos.write(Base64.decodeBase64(screen.getBytes()));
        fos.close();
    }

    private static class Context {
        private Selenium selenium;
        private String description;
        private File root;
        private String browser;
        private String app;

        private Context() {
        }

        private Context(Selenium selenium, String description, File root, String browser, String app) {
            this.selenium = selenium;
            this.description = description;
            this.root = root;
            this.browser = browser;
            this.app = app;
        }
    }
}
