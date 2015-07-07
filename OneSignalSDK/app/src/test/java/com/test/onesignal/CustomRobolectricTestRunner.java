package com.test.onesignal;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;

public class CustomRobolectricTestRunner extends RobolectricGradleTestRunner {
    public CustomRobolectricTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    public InstrumentationConfiguration createClassLoaderConfig() {
        return InstrumentationConfiguration.newBuilder().addInstrumentedPackage("com.onesignal").build();
    }
}
