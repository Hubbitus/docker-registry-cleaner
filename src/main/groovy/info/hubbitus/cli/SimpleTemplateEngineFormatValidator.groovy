package info.hubbitus.cli

import com.beust.jcommander.IParameterValidator
import com.beust.jcommander.ParameterException
import groovy.text.SimpleTemplateEngine

/**
 * @author Pavel Alexeev.
 * @since 2018-02-28 02:26.
 */
class SimpleTemplateEngineFormatValidator implements IParameterValidator {
	void validate(String name, String value) throws ParameterException {

		try{
			new SimpleTemplateEngine().createTemplate(value)
		}
		catch (Throwable t){
			throw new ParameterException("You probably provide incorrect template in --format='$value' option. We got exception on test evaluation:" + t.message)
		}
	}
}
