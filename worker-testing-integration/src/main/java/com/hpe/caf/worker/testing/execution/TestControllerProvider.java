package com.hpe.caf.worker.testing.execution;

import com.hpe.caf.worker.testing.TestController;
import com.hpe.caf.worker.testing.TestControllerSingle;
import com.hpe.caf.worker.testing.TestItemProvider;

/**
 * The test controller provider interface.
 * Implementations are responsible for creating configured {@link TestController} instances
 * used to execute worker tests or preparation of initial test case data.
 * Implementations of this interface should be advertised to {@link com.hpe.caf.util.ModuleLoader}
 * and {@link java.util.ServiceLoader} using resource configuration file (META-INF/services).
 */
public interface TestControllerProvider {

    /**
     * Gets the name of a worker under test.
     * When worker test application is invoked this name will be used to identify
     * which worker should be tested.
     *
     * @return the name of a worker
     */
    String getWorkerName();

    /**
     * Gets test controller.
     *
     * @return the test controller
     * @throws Exception
     */
    TestController getTestController() throws Exception;

    /**
     * Gets initial test case data preparation controller. This controller will be used when tests are running in
     * the data generation mode.
     *
     * @return the data preparation controller
     * @throws Exception
     */
    TestController getDataPreparationController()  throws Exception;

    /*
    * Gets the configuration for the item provider. This can be used in the setup for the Test Classes
    * @return the TestConfiguration
    */
    TestItemProvider getItemProvider(boolean typeOfItemProvider);

    /*
    * Gets the controller
    *
    * @return the data preparation controller
    * @throws Exception
    * */
    TestControllerSingle getNewDataPreparationController() throws Exception;

    /*
    * Gets the controller
    *
    * @return the test controller
    * @throws Exception
    * */
    TestControllerSingle getNewTestController() throws Exception;


}