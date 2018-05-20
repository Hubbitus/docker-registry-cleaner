package info.hubbitus

import com.beust.jcommander.ParameterException

/**
 * @author Pavel Alexeev.
 * @since 2017-09-16 13:44.
 */

try{
	new RegistryCleaner(args).clean()
}
catch (ParameterException e){
	RegistryCleaner.log.error("Input parameters are incorrect: $e")
	System.exit(1)
}
