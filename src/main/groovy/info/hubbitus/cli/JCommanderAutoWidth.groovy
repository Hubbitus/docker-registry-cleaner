package info.hubbitus.cli

import com.beust.jcommander.JCommander
import groovy.transform.InheritConstructors

/**
 * Unix-hack to use full terminal width (by suggestion from http://stackoverflow.com/questions/1286461/can-i-find-the-console-width-with-java)
 * It was implemented in similar fashion (https://issues.apache.org/jira/browse/CLI-166), but then reverted because can't
 * be used for all systems in java-way
 *
 * @link https://github.com/cbeust/jcommander/issues/217#issuecomment-330100807
 *
 * @author Pavel Alexeev.
 * @since 2017-09-18 01:44.
 */
@InheritConstructors
class JCommanderAutoWidth extends JCommander{
    @Override
    int getColumnSize() {
        try{
            return ["bash", "-c", "tput cols 2> /dev/tty"].execute().text.toInteger()
        }
        catch(IOException | NumberFormatException ignore){
            /**
             * Already hardcoded default, but Options private.
             * @see com.beust.jcommander.JCommander.Options#columnSize
             */
            return 79
        }
    }
}
