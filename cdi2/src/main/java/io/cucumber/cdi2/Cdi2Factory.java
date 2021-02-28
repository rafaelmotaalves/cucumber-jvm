package io.cucumber.cdi2;

import io.cucumber.core.backend.ObjectFactory;
import org.apiguardian.api.API;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Unmanaged;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@API(status = API.Status.STABLE)
public final class Cdi2Factory implements ObjectFactory {

    private final Map<Class<?>, Unmanaged.UnmanagedInstance<?>> standaloneInstances = new HashMap<>();
    private final Set<Class<?>> unmanagedclasses = new HashSet<>();
    private final Set<Class<?>> managedClasses = new HashSet<>();
    private SeContainer container;

    @Override
    public void start() {
        if (container == null) {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance();
            initializer.addBeanClasses(unmanagedclasses.toArray(new Class[unmanagedclasses.size()]));
            container = initializer.initialize();
        }
    }

    @Override
    public void stop() {
        if (container != null) {
            container.close();
            container = null;
        }
        for (Unmanaged.UnmanagedInstance<?> unmanaged : standaloneInstances.values()) {
            unmanaged.preDestroy();
            unmanaged.dispose();
        }
        standaloneInstances.clear();
    }

    @Override
    public boolean addClass(final Class<?> clazz) {
    	if (!managedClasses.contains(clazz) && !unmanagedclasses.contains(clazz)) {
    		start();
    		Instance<?> selected = container.select(clazz);
    		if (selected.isUnsatisfied()) {
    			unmanagedclasses.add(clazz);
    		} else {
    			managedClasses.add(clazz);
    		}
    		stop();
    	}
        return true;
    }

    @Override
    public <T> T getInstance(final Class<T> type) {
        Unmanaged.UnmanagedInstance<?> instance = standaloneInstances.get(type);
        if (instance != null) {
            return type.cast(instance.get());
        }
        Instance<T> selected = container.select(type);
        if (selected.isUnsatisfied()) {
            BeanManager beanManager = container.getBeanManager();
            Unmanaged<T> unmanaged = new Unmanaged<>(beanManager, type);
            Unmanaged.UnmanagedInstance<T> value = unmanaged.newInstance();
            value.produce();
            value.inject();
            value.postConstruct();
            standaloneInstances.put(type, value);
            return value.get();
        }
        return selected.get();
    }

}
