package info.hubbitus.cli

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.ParameterException

/**
 * @author Pavel Alexeev.
 * @since 2017-09-18 20:38.
 */
class SortOptionValidator implements IParameterValidator {
    void validate(String name, String value) throws ParameterException {
        if (!(value in ['name', 'time'])) {
            throw new ParameterException("Parameter " + name + " must be either 'name' or 'time' (got '" + value +"')");
        }
    }
}