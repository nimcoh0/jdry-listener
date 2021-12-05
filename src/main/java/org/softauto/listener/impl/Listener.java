package org.softauto.listener.impl;

import com.sun.tools.attach.VirtualMachine;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.softauto.listener.AbstractModule;
import org.softauto.listener.Listeners;
import org.softauto.listener.MultipleRecursiveToStringStyle;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class Listener {

    static org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(Listener.class);
    /** marker for trace logs */
    private static final Marker TRACER = MarkerManager.getMarker("TRACER");
    static Object serviceImpl;



    private  static ExecutorService executor = Executors.newFixedThreadPool(100);

    static public void init(Object serviceImpl){
        init(serviceImpl,true);
    }


    static public void init(Object serviceImpl,boolean loadWeaver){
        try {
            Listener.serviceImpl = serviceImpl;
            String javaHome = System.getenv("JAVA_HOME");
            addJarToClasspath(javaHome+"/lib/tools.jar");
            loadLib(System.getenv("temp"),"aspectjweaver-1.9.6.jar");
            if(loadWeaver) {
                startWeaver(System.getenv("temp") + "/aspectjweaver-1.9.6.jar");
            }else {
                logger.info("Weaver not attache by configuration .  make sure you load it before the app start ");
            }
        }catch (Exception e){
            logger.error("ServiceImpl not found ",e);
        }
    }


    public  static Object captureAll(org.aspectj.lang.ProceedingJoinPoint joinPoint){
        Object result =null;
        AtomicReference<String> fqmn = new AtomicReference();
        try {
            if(serviceImpl != null) {
                Method method = serviceImpl.getClass().getDeclaredMethod("executeBefore", new Class[]{String.class, Object[].class, Class[].class});
                MethodSignature sig = (MethodSignature) joinPoint.getSignature();
                fqmn.set(buildMethodFQMN(sig.getName(), sig.getDeclaringType().getName()));
                logger.trace(TRACER, "IN " + fqmn.get() + "( " + Arrays.toString(sig.getMethod().getParameterTypes()) + ")[" + Arrays.toString(joinPoint.getArgs()) + "]");
                AtomicReference<Object[]> ref = new AtomicReference();
                ref.set(null);
                if(Listeners.isExist(sig)) {
                    executor.submit(() -> {
                        try {
                            logger.debug("invoke listener on "+serviceImpl+ " fqmn: "+ fqmn.get() + " args:" + joinPoint.getArgs().toString() + " types:" + sig.getMethod().getParameterTypes());
                            method.setAccessible(true);
                            ref.set((Object[]) method.invoke(serviceImpl, new Object[]{fqmn.get(), getArgs(joinPoint.getArgs()), getTypes(sig.getMethod().getParameterTypes())}));
                        } catch (Exception e) {
                            logger.error("send message " + fqmn.get() + " fail  ", e);
                        }
                    });
                }
                Object[] o = ref.get();
                if (o != null && o.length > 0 && o[0] != null) {
                  result = joinPoint.proceed(o);
                } else {
                    result = joinPoint.proceed();
                }
                logger.trace(TRACER, "OUT " + fqmn.get() + " (" + sig.getReturnType() + ") " + result2String(result));
            }else {
                result = joinPoint.proceed();
            }
        } catch (Throwable e) {
            logger.error("capture message "+fqmn.get()+" fail  ",e );
        }
        returning(joinPoint,result);
        return result;
    }

    public  static void returning(JoinPoint joinPoint,Object result) {
        try {
            if(serviceImpl != null) {
                Method method = serviceImpl.getClass().getDeclaredMethod("executeAfter", new Class[]{String.class, Object[].class, Class[].class});
                MethodSignature sig = (MethodSignature) joinPoint.getSignature();
                if(Listeners.isExist(sig)) {
                    String fqmn = buildMethodFQMN(sig.getName(), sig.getDeclaringType().getName());
                    if (!sig.getMethod().getReturnType().getName().equals("void")) {
                        //if (result != null)
                            executor.submit(() -> {
                                try {
                                    logger.debug("invoke returning listener on "+serviceImpl+ " fqmn:" + fqmn + " args:" + joinPoint.getArgs().toString() + " types:" + sig.getMethod().getParameterTypes());
                                    method.setAccessible(true);
                                    method.invoke(serviceImpl, new Object[]{fqmn, new Object[]{result}, new Class[]{sig.getMethod().getReturnType()}});
                                } catch (Exception e) {
                                    logger.error("sendResult returning fail for " + fqmn , e);
                                }
                            });
                    } else {
                        executor.submit(() -> {
                            try {
                                logger.debug("invoke returning listener on "+serviceImpl+ " fqmn:" + fqmn + " args:" + result2String(joinPoint.getArgs()) + " types:" + result2String(sig.getMethod().getParameterTypes()));
                                method.setAccessible(true);
                                method.invoke(serviceImpl, new Object[]{fqmn , getArgs(joinPoint.getArgs()), getTypes(sig.getMethod().getParameterTypes())});
                             } catch (Exception e) {
                                logger.error("sendResult returning fail for " + fqmn , e);
                            }
                        });
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }



    public static String buildMethodFQMN(String methodName, String clazz){
        return clazz.replace(".","_")+"_"+methodName;
    }

    public static String result2String(Object result){
        try{

            if(result != null){
                if(result instanceof List){
                    return ToStringBuilder.reflectionToString(((List)result).toArray(), new MultipleRecursiveToStringStyle());
                }else {
                    return ToStringBuilder.reflectionToString(result, new MultipleRecursiveToStringStyle());
                }
            }
        }catch(Exception e){
            logger.warn("result to String fail on  ",e.getMessage());
        }
        return "";
    }

    public static void addJarToClasspath(String f) {
        try {
            URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            addURL(new File(f).toURL(),classLoader);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }


    public static void addURL(URL u, URLClassLoader sysloader)  {
        Class[] parameters = new Class[]{URL.class};
        Class sysclass = URLClassLoader.class;
        try {
            Method method = sysclass.getDeclaredMethod("addURL",parameters);
            method.setAccessible(true);
            method.invoke(sysloader,new Object[]{ u });
        } catch (Throwable t) {
            logger.error("add url fail "+ u ,t);
        }
    }

    public static  void startWeaver(String aspectjweaver){
        try {

            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            VirtualMachine jvm = VirtualMachine.attach(pid);
            jvm.loadAgent(aspectjweaver);
            jvm.detach();
            logger.info("Weaver Load successfully ");
        }catch (Exception e){
            logger.fatal("load Weaver fail ",e);
            System.exit(1);
        }

    }


    private static void loadLib(String path, String name) {
        InputStream input = Listener.class.getClassLoader().getResourceAsStream(name);
        OutputStream outputStream = null;
        try {
            File fileOut = new File(path+"/"+name);
            outputStream = new FileOutputStream(fileOut);
            org.apache.commons.io.IOUtils.copy(input, outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static public void addListener(String methodName, Object[] types){
        Listeners.addListener(methodName,types);
    }

    static public void addSchema(Class iface){
        Listeners.addSchema(iface);

    }

    static public void addModule(AbstractModule module){
        Listeners.addModule(module);
      }

    /**
     * get the arguments types
     **/
    public static Class[] getTypes(Object obj){
        Class[] types;
        if(obj instanceof Class<?>){
            types = new Class[1];
            types[0] = (Class)obj;
        }else {
            return (Class[])obj;
        }

        return types;
    }

    /**
     * get the arguments values
     **/
    public static Object[] getArgs(Object obj){
        Object[] args;

        if(!(obj instanceof Object[])){
            args = new Object[1];
            args[0] = obj;
        }else {
            return (Object[])obj;
        }

        return args;
    }
}
