package org.softauto.listener;

import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Listeners {


        static private List<HashMap<String,Object>> listeners = new ArrayList<>();

        static public void addListener(String methodName, Object[] types){
            HashMap<String,Object> hm = new HashMap<>();
            hm.put(methodName,types);
            listeners.add(hm);
        }

        static public void addSchema(Class iface){
            for(Method m : iface.getDeclaredMethods()){
                addListener(m.getName(),m.getParameterTypes());
            }
        }

        static public void addModule(AbstractModule module){
            module.configuration();
        }

        static public boolean isExist(MethodSignature sig){
            AtomicReference<Boolean> ref = new AtomicReference();
            for(HashMap<String,Object> listener : listeners){
                listener.forEach((k,v)->{
                    if(k.equals(sig.getName()) && Arrays.equals(((Object[])v),sig.getParameterTypes())){
                         ref.set(true);
                    }
                });
            }
            return ref.get();
        }


        static public List<HashMap<String,Object>> getMessages(){
            return listeners;
        }



}
