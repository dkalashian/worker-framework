package com.hpe.caf.worker.testing.validation;

import com.hpe.caf.api.Codec;
import com.hpe.caf.api.worker.DataStore;
import com.hpe.caf.worker.testing.configuration.ValidationSettings;

import java.util.Map;

/**
 * Created by ploch on 07/12/2015.
 */
public class ValidatorFactory {


    private ValidationSettings validationSettings;
    private final DataStore dataStore;
    private final Codec codec;
    private final String testDataFolder;

    public ValidatorFactory(ValidationSettings validationSettings, DataStore dataStore, Codec codec, String testDataFolder) {
        this.validationSettings = validationSettings;
        this.dataStore = dataStore;
        this.codec = codec;
        this.testDataFolder = testDataFolder;
    }

    public PropertyValidator createRootValidator() {
        return new PropertyMapValidator(this);
    }

    public PropertyValidator create(String propertyName, Object sourcePropertyValue, Object validatorPropertyValue) {
        if (validationSettings.getIgnoredProperties().contains(propertyName)) {
            return new IgnorePropertyValidator();
        }
        if (validationSettings.getReferencedDataProperties().contains(propertyName)) {
            return new ReferenceDataValidator(dataStore, codec, testDataFolder);
        }

        if (sourcePropertyValue instanceof Map && validatorPropertyValue instanceof Map) {
            return new PropertyMapValidator(this);
        }

        return new ValuePropertyValidator();

    }

}
