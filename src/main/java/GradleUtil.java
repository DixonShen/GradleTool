import org.apache.commons.cli.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Gradle 工具
 * Created by z003r98d on 6/15/2017.
 */
public final class GradleUtil {

    /**
     * 内部类，构建任务类
     */
    static class BuildTask implements Runnable {

        private String projectPath;
        private StringBuilder buildLog;
        private Process process;

        public long startTime;

        public BuildTask(String path, long startTime) {
            this.projectPath = path;
            this.startTime = startTime;
        }

        @Override
        public void run() {
            process = null;
            TimeoutMonitor daemon = new TimeoutMonitor(this);
            System.out.println("运行守护线程");
            daemon.setDaemon(true);
            daemon.start();
            System.out.println("开始build");
            try {
                buildLog = (StringBuilder) executeCMD(BUILD_COMMAND, projectPath, process);
                daemon.stopMonitor();
                FileWriter fw = new FileWriter(new File(projectPath + "/buildLog.txt"));
                fw.write(buildLog.toString());
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void stopTask(){
            process.destroy();
        }


    }

    public static Map<String, String> options = new HashMap<>();

    public static String scanDir;                                   // 扫描目录
    public static String BUILD_COMMAND;                             // build命令
    public static int timeout;                                      // build超时
    public static int poolSize;                                     // 线程池大小
    public static List<String> excludeList = new ArrayList<>();     // 排除目录

    /**
     * 构造函数
     */
    private GradleUtil() {

    }

    /**
     * 找到给定目录下的所有安卓项目
     * @param path
     * @return 返回所有项目目录
     */
    public static List<String> findAllAndroidProjects(String path) {
        for (String s : excludeList) {
            if (path.contains(s))
                return null;
        }
//        if (path.contains("OldVersions") || path.contains("iOS")) return null;
        List<String> result = new ArrayList<>();
        File file = new File(path);
        if (file.exists()) {
            if (isAndroidProject(file)) {
                result.add(file.getAbsolutePath());
            } else {
                for (File subFile : file.listFiles()) {
                    if (subFile.isDirectory()) {
                        List<String> tempRes=findAllAndroidProjects(subFile.getAbsolutePath());
                        if (tempRes != null)
                            result.addAll(tempRes);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 判断指定目录是否为安卓工程根目录
     * @param file
     * @return
     */
    public static boolean isAndroidProject(File file) {
        File[] files = file.listFiles();
        for (File file1 : files) {
            if (file1.getName().equals("build.gradle"))
                return true;
        }
        return false;
    }

    /**
     * 执行linux下command
     * @param cmd
     */
    public static Object executeCMD(String cmd, String path, Process process) {
        try {
            String[] paths = path.split("/");
            String project = paths[paths.length-1];
            String[] cmdA = {"/bin/sh", "-c", cmd};
            process = Runtime.getRuntime().exec(cmdA, null, new File(path));
            LineNumberReader br = new LineNumberReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            System.out.println(df.format(new Date()));
            sb.append("Build Task: " + df.format(new Date()) + "\n\n");
            String line = "";
            while ((line=br.readLine()) != null) {
                System.out.println(project + ": " + line);
                sb.append(line).append("\n");
            }
            return sb;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 从文件中读取配置，部分会被CommandLine的设置覆盖
     * @param commandLine
     * @throws Exception
     */
    public static void InitializeOptions(CommandLine commandLine) throws Exception{
        Properties properties = new Properties();
        InputStream in = new BufferedInputStream(new FileInputStream("config.properties"));
        properties.load(in);
        Iterator<String> iterator = properties.stringPropertyNames().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
//            System.out.println(key + ": " + properties.getProperty(key));
            if (key.equals("exclude-list")) {
                String[] dirs = properties.getProperty(key).split(";");
                for (String s : dirs) {
                    if (!s.trim().equals(""))
                        excludeList.add(s);
                }
                continue;
            }
            options.put(key, properties.getProperty(key));
        }
        in.close();
        if (commandLine.hasOption("e")) {
            excludeList.clear();
            String[] excludes = commandLine.getOptionValue("e").split(";");
            for (String s : excludes)
                excludeList.add(s);
        }
        if (commandLine.hasOption("t")) {
            options.put("timeout", commandLine.getOptionValue("t"));
        }
        if (commandLine.hasOption("c")) {
            options.put("command", commandLine.getOptionValue("c"));
            System.out.println(commandLine.getOptionValue("c"));
        }
        if (commandLine.hasOption("ts")) {
            options.put("threads", commandLine.getOptionValue("ts"));
        }
        if (commandLine.hasOption("dir")) {
            options.put("target-dir", commandLine.getOptionValue("dir"));
        }

    }

    /**
     * @param args xx
     */
    public static void main(String[] args) throws Exception{
        Options opts = new Options();
        CommandLineParser parser = new DefaultParser();
        opts.addOption("h", "help", false, "print usage information");
        opts.addOption("e", "exclude-list", true, "specify the exclude list separated by ;");
        opts.addOption("t", "timeout", true, "set the build timeout");
        opts.addOption("c", "command", true, "set the build command");
        opts.addOption("ts", "threads", true, "set the thread pool size");
        opts.addOption("dir", "target-dir", true, "set the target dir needed scan to build");
        CommandLine commandLine = parser.parse(opts, args);

        // 打印帮助信息
        if (commandLine.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar <FILENAME> [OPTION]", opts);
            return;
        }

        // 读取并覆盖文件中的部分配置
        InitializeOptions(commandLine);

        scanDir = options.get("target-dir");
        BUILD_COMMAND = options.get("command");
        timeout = Integer.parseInt(options.get("timeout"));
        poolSize = Integer.parseInt(options.get("threads"));


        File file = new File(scanDir);

        List<String> res = findAllAndroidProjects(file.getAbsolutePath());
        if (res != null) {
            for (String s : res) {
                System.out.println(s);
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        long startTime = System.currentTimeMillis();
        for (String project1 : res) {
            long startTime1 = System.currentTimeMillis();
            Runnable buildTask = new BuildTask(project1, startTime1);
            pool.execute(buildTask);
        }
        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Total time: " + (endTime - startTime) + "ms");
    }

}
