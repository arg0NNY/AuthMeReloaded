package fr.xephi.authme.message.updater;

import ch.jalu.configme.SettingsManager;
import ch.jalu.configme.properties.Property;
import ch.jalu.configme.properties.StringProperty;
import ch.jalu.configme.resource.YamlFileResource;
import com.google.common.collect.ImmutableList;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.message.MessageKey;

import java.io.File;
import java.util.List;

/**
 * Migrates the used messages file to a complete, up-to-date version when necessary.
 */
public class MessageUpdater {

    private static final List<Property<String>> TEXT_PROPERTIES = buildPropertyEntriesForMessageKeys();

    /**
     * Applies any necessary migrations to the user's messages file and saves it if it has been modified.
     *
     * @param userFile the user's messages file (yml file in the plugin's folder)
     * @param localJarPath path to the messages file in the JAR for the same language (may not exist)
     * @param defaultJarPath path to the messages file in the JAR for the default language
     * @return true if the file has been migrated and saved, false if it is up-to-date
     */
    public boolean migrateAndSave(File userFile, String localJarPath, String defaultJarPath) {
        JarMessageSource jarMessageSource = new JarMessageSource(localJarPath, defaultJarPath);
        return migrateAndSave(userFile, jarMessageSource);
    }

    /**
     * Performs the migration.
     *
     * @param userFile the file to verify and migrate
     * @param jarMessageSource jar message source to get texts from if missing
     * @return true if the file has been migrated and saved, false if it is up-to-date
     */
    boolean migrateAndSave(File userFile, JarMessageSource jarMessageSource) {
        // YamlConfiguration escapes all special characters when saving, making the file hard to use, so use ConfigMe
        YamlFileResource userResource = new YamlFileResource(userFile);
        SettingsManager settingsManager = SettingsManager.createWithProperties(userResource, null, TEXT_PROPERTIES);

        // Step 1: Migrate any old keys in the file to the new paths
        boolean movedOldKeys = migrateOldKeys(userResource);
        // Step 2: Take any missing messages from the message files shipped in the AuthMe JAR
        boolean addedMissingKeys = addMissingKeys(jarMessageSource, userResource, settingsManager);

        if (movedOldKeys || addedMissingKeys) {
            settingsManager.save();
            ConsoleLogger.debug("Successfully saved {0}", userFile);
            return true;
        }
        return false;
    }

    private boolean migrateOldKeys(YamlFileResource userResource) {
        boolean hasChange = OldMessageKeysMigrater.migrateOldPaths(userResource);
        if (hasChange) {
            ConsoleLogger.info("Old keys have been moved to the new ones in your messages_xx.yml file");
        }
        return hasChange;
    }

    private boolean addMissingKeys(JarMessageSource jarMessageSource, YamlFileResource userResource,
                                   SettingsManager settingsManager) {
        int addedKeys = 0;
        for (Property<String> property : TEXT_PROPERTIES) {
            if (!property.isPresent(userResource)) {
                settingsManager.setProperty(property, jarMessageSource.getMessageFromJar(property));
                ++addedKeys;
            }
        }
        if (addedKeys > 0) {
            ConsoleLogger.info("Added " + addedKeys + " missing keys to your messages_xx.yml file");
            return true;
        }
        return false;
    }

    private static List<Property<String>> buildPropertyEntriesForMessageKeys() {
        ImmutableList.Builder<Property<String>> listBuilder = ImmutableList.builder();
        for (MessageKey messageKey : MessageKey.values()) {
            listBuilder.add(new StringProperty(messageKey.getKey(), ""));
        }
        return listBuilder.build();
    }
}