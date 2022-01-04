package org.softauto.listener;

import org.aspectj.lang.reflect.MethodSignature;
import org.softauto.listener.impl.Listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Listeners {

        static org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(Listeners.class);
        static private List<HashMap<String,Object>> listeners = new ArrayList<>();

        static public void addListener(String methodName, Object[] types){
            HashMap<String,Object> hm = new HashMap<>();
            hm.put(methodName,types);
            listeners.add(hm);
        }

    static public void addListener(HashMap<String,Object> hm){
        listeners.add(hm);
    }


        static public void resetListener(){
            listeners = new ArrayList<>();
        }

        static public void removeListener(String fqmn){
            listeners.forEach(k->{
                k.forEach((methodName,types)->{
                    if(methodName.equals(fqmn)){
                        k.put(fqmn,null);
                    }
                });
            });
        }


        static public void addSchema(Class iface){
            for(Method m : iface.getDeclaredMethods()){
                addListener(m.getName(),m.getParameterTypes());
            }
        }

    static public void addSchema(HashMap<String,Object> hm){
         addListener(hm);
    }

        static public void addModule(AbstractModule module){
            module.configuration();
        }

        static public boolean isExist(MethodSignature sig){
            AtomicReference<Boolean> ref = new AtomicReference();
            ref.set(false);
            for(HashMap<String,Object> listener : listeners){
                listener.forEach((k,v)->{
                    if(v != null) {
                        String fqmn = buildMethodFQMN(sig.getName(), sig.getDeclaringType().getName());
                        if(k.equals(fqmn) && Arrays.equals(((Class[])v),sig.getParameterTypes())) {
                            ref.set(true);
                            logger.debug("found listener " + fqmn);
                        }
                    }
                 });
                if(ref.get()){
                    return ref.get();
                }
            }

            return ref.get();
        }


        static public List<HashMap<String,Object>> getMessages(){
            return listeners;
        }

    public static String buildMethodFQMN(String methodName, String clazz){
        return clazz.replace(".","_")+"_"+methodName;
    }

}
