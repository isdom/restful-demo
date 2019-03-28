/**
 *
 */
package org.jocean.restfuldemo.ctrl;

import java.util.Arrays;
import java.util.Comparator;

import org.jocean.cli.CliCommand;
import org.jocean.cli.CliContext;
import org.jocean.cli.CommandRepository;

/**
 * @author Marvin.Ma
 *
 */
public class Help2 implements CliCommand<CliContext> {

    @Override
    public String execute(final CliContext ctx, final String... args) throws Exception {
        final CommandRepository repo = ctx.getCommandRepository();

        if ( args.length >= 1 ) {
            final CliCommand<?> cmd = repo.findCommandByAction(args[0]);

            if ( null == cmd ) {
                return  "failed: can not found command [" + args[0] + "]";
            }
            else {
                return  "command [" + args[0] + "]: " + cmd.getHelp();
            }
        }
        else {
            final StringBuilder sb = new StringBuilder();
            final CliCommand<? extends CliContext>[] cmds = repo.getCommands();
            Arrays.sort(cmds, new Comparator<CliCommand<? extends CliContext>>() {

                @Override
                public int compare(final CliCommand<? extends CliContext> cmd1,
                        final CliCommand<? extends CliContext> cmd2) {
                    return cmd1.getAction().compareTo(cmd2.getAction());
                }} );

            for ( final CliCommand<?> cmd : cmds ) {
                sb.append("[");
                sb.append(cmd.getAction());
                sb.append("]: ");
                sb.append(cmd.getHelp());
                sb.append("\r\n");
            }
            return sb.toString();
        }
    }

    @Override
    public String getAction() {
        return "help2";
    }

    @Override
    public String getHelp() {
        return null;
    }

}
