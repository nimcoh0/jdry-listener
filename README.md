# jdry-listener
the Listener module is a generic module that enable you to listen to any method .
there are three way's to set the listeners
 from avpr schema file . create the schema files by annotated the method with @ListenerForTesting 
 see wiki for more details 

     org.softauto.listener.impl.Listener.addSchema(Class iface);

add method signature 

    org.softauto.listener.impl.Listener.addListener(String methodName, Object[] types)

use module  : create class and extend AbstractModule . 

    org.softauto.listener.impl.Listener.addModule(AbstractModule module) 

in that class create method configuration
and in that method set method signature 
		

    listen(String fqmn, Object[] types)
