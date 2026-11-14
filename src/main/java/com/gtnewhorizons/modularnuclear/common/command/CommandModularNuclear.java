package com.gtnewhorizons.modularnuclear.common.command;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.config.DefaultConfigFiles;
import com.gtnewhorizons.modularnuclear.common.config.DefaultConfigFiles.Mode;

import cpw.mods.fml.common.Loader;

/**
 * {@code /modularnuclear config generate [missing|force]}: writes the config files with the built-in defaults, for
 * server admins to customize. See Note [Config files only override the built-in defaults]
 */
public class CommandModularNuclear extends CommandBase {

    private static final String USAGE = "/modularnuclear config generate [missing|force]";

    @Override
    public String getCommandName() {
        return "modularnuclear";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    /** The level of /ban and /op: writing the server's config files is for its admins. */
    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3 || !args[0].equals("config") || !args[1].equals("generate")) {
            throw new WrongUsageException(USAGE);
        }
        Mode mode = Mode.IF_NONE_EXIST;
        if (args.length == 3) {
            switch (args[2]) {
                case "missing" -> mode = Mode.MISSING_ONLY;
                case "force" -> mode = Mode.OVERWRITE;
                default -> throw new WrongUsageException(USAGE);
            }
        }

        File configDir = Loader.instance()
            .getConfigDir();
        DefaultConfigFiles.Result result;
        try {
            result = DefaultConfigFiles.generate(configDir, mode);
        } catch (IOException e) {
            ModularNuclear.LOG.error("Failed to write the default config files", e);
            throw new CommandException("Could not write the config files: " + e.getMessage());
        }

        if (result.refused) {
            tell(sender, "Wrote nothing: these config files already exist in " + configDir + ":");
            for (String path : result.existing) tell(sender, "  " + path);
            tell(sender, "Add 'missing' to write only the missing files, or 'force' to overwrite them all.");
            return;
        }
        if (result.written.isEmpty()) {
            tell(sender, "Every config file already exists in " + configDir + ": nothing to write.");
            return;
        }
        tell(
            sender,
            "Wrote " + result.written.size() + " config files with the built-in defaults to " + configDir + ":");
        for (String path : result.written) tell(sender, "  " + path);
        if (mode == Mode.MISSING_ONLY && !result.existing.isEmpty()) {
            tell(sender, "Left the " + result.existing.size() + " existing files as they were.");
        }
        tell(sender, "They take effect after a restart.  Delete the ones you don't change, so they keep the defaults.");
    }

    private static void tell(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return switch (args.length) {
            case 1 -> getListOfStringsMatchingLastWord(args, "config");
            case 2 -> getListOfStringsMatchingLastWord(args, "generate");
            case 3 -> getListOfStringsMatchingLastWord(args, "missing", "force");
            default -> null;
        };
    }
}
