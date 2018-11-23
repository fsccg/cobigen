package com.devonfw.cobigen.eclipse.healthcheck;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devonfw.cobigen.api.HealthCheck;
import com.devonfw.cobigen.api.constants.BackupPolicy;
import com.devonfw.cobigen.api.constants.ConfigurationConstants;
import com.devonfw.cobigen.api.exception.InvalidConfigurationException;
import com.devonfw.cobigen.api.to.HealthCheckReport;
import com.devonfw.cobigen.eclipse.common.constants.external.CobiGenDialogConstants.HealthCheckDialogs;
import com.devonfw.cobigen.eclipse.common.constants.external.ResourceConstants;
import com.devonfw.cobigen.eclipse.common.exceptions.GeneratorProjectNotExistentException;
import com.devonfw.cobigen.eclipse.common.tools.MessageUtil;
import com.devonfw.cobigen.eclipse.common.tools.PlatformUIUtil;
import com.devonfw.cobigen.eclipse.common.tools.ResourcesPluginUtil;
import com.devonfw.cobigen.impl.CobiGenFactory;
import com.devonfw.cobigen.impl.config.constant.ContextConfigurationVersion;
import com.devonfw.cobigen.impl.config.upgrade.ContextConfigurationUpgrader;
import com.devonfw.cobigen.impl.exceptions.BackupFailedException;

/**
 * This class implements the Health Check to provide more information about the current status of CobiGen and
 * potentially why it cannot be used with the current selection.
 */
public class HealthCheckDialog {

	/** Logger instance. */
	private static final Logger LOG = LoggerFactory.getLogger(HealthCheckDialog.class);

	/** HealthCheckReport instance */
	private HealthCheckReport report;

	/** HealthCheck instance */
	private HealthCheck healthCheck;

	/**
	 * Creates a new {@link HealthCheckDialog} with the given parameters.
	 */
	public HealthCheckDialog() {
		healthCheck = CobiGenFactory.createHealthCheck();
	}

	/**
	 * Executes the simple health check, checking configuration project existence,
	 * validity of context configuration, as well as validity of the current
	 * workbench selection as generation input.
	 */
	public void execute() {

		String firstStep = "1. CobiGen configuration project '" + ResourceConstants.CONFIG_PROJECT_NAME + "'... ";
		String secondStep = "\n2. CobiGen context configuration '" + ConfigurationConstants.CONTEXT_CONFIG_FILENAME
				+ "'... ";

		String healthyCheckMessage = "";
		IProject generatorConfProj = null;

		try {

			// check configuration project existence
			generatorConfProj = ResourcesPluginUtil.getGeneratorConfigurationProject();

			// refresh and check context configuration
			ResourcesPluginUtil.refreshConfigurationProject();
			String pathForCobigenTemplates = "";

			IPath ws = ResourcesPluginUtil.getWorkspaceLocation();
			pathForCobigenTemplates = ws.toPortableString();

			if (generatorConfProj != null && generatorConfProj.getLocationURI() != null) {
				CobiGenFactory.create(generatorConfProj.getLocationURI());
			} else {
				String fileName = ResourcesPluginUtil.downloadJar(false);

				File jarPath = new File(
						ws.append(ResourceConstants.DOWNLOADED_JAR_FOLDER + File.separator + fileName).toString());
				CobiGenFactory.create(jarPath.toURI());
				boolean fileExists = jarPath.exists();
				if (!fileExists) {
					MessageDialog.openWarning(Display.getDefault().getActiveShell(), "Warning",
							"Not Downloaded the CobiGen Template Jar");
				}

				FileSystem fileSystem = FileSystems.getDefault();
				Path cobigenFolderPath = null;
				if (fileSystem != null && fileSystem.getPath(pathForCobigenTemplates) != null) {
					cobigenFolderPath = fileSystem.getPath(pathForCobigenTemplates);
				}

				List<String> templateNames = new ArrayList<>();
				try (ZipFile file = new ZipFile(jarPath)) {
					// deleteDirectoryStream(configFolder);
					Enumeration<? extends ZipEntry> entries = file.entries();
					if (Files.notExists(cobigenFolderPath)) {
						Files.createDirectory(cobigenFolderPath);
					}
					while (entries.hasMoreElements()) {
						ZipEntry entry = entries.nextElement();
						Path saveForFileCreationPath = fileSystem.getPath(cobigenFolderPath + File.separator
								+ "CobiGen_Templates" + File.separator + entry.getName());
						if (templateNames.parallelStream().anyMatch(entry.getName()::contains)
								|| entry.getName().contains("context.xml")) {
							saveForFileCreationPath = fileSystem.getPath(cobigenFolderPath + File.separator
									+ "CobiGen_Templates" + File.separator + File.separator + entry.getName());
						} else if (entry.getName().contains("com/")) {
							saveForFileCreationPath = fileSystem.getPath(cobigenFolderPath + File.separator
									+ "CobiGen_Templates" + File.separator + "src" + File.separator + "main"
									+ File.separator + "java" + File.separator + entry.getName());
						}
						if (entry.isDirectory()) {
							Files.createDirectories(saveForFileCreationPath);
						} else {
							Files.deleteIfExists(saveForFileCreationPath);
							try (InputStream is = file.getInputStream(entry);
									BufferedInputStream bis = new BufferedInputStream(is);) {
								Files.createFile(saveForFileCreationPath);
								FileOutputStream fileOutput = new FileOutputStream(saveForFileCreationPath.toString());
								while (bis.available() > 0) {
									fileOutput.write(bis.read());
								}
								fileOutput.close();
							}
						}
					}
				} catch (IOException e) {

					LOG.error("An exception occurred while processing Jar files to create Cobigen_Templates folder", e);
					// PlatformUIUtil.openErrorDialog("An exception occurred while processing Jar
					// file", e);
				}
			}
			
			healthyCheckMessage = firstStep + "OK.";			
			healthyCheckMessage += secondStep;
			boolean healthyCheckWarning = false;
			File tempFile = new File(
					pathForCobigenTemplates + "/CobiGen_Templates/" + ConfigurationConstants.CONTEXT_CONFIG_FILENAME);
			boolean contextFileExists = tempFile.exists();
			File newtempFile = new File(
					pathForCobigenTemplates + "/CobiGen_Templates/" + ConfigurationConstants.TEMPLATE_RESOURCE_FOLDER
							+ "/" + ConfigurationConstants.CONTEXT_CONFIG_FILENAME);
			boolean newcontextFileExists = newtempFile.exists();
			if (newcontextFileExists || contextFileExists) {
				healthyCheckMessage += "OK.";
			} else {
				healthyCheckMessage += "INVALID.";
				healthyCheckWarning = true;
			}
			openSuccessDialog(healthyCheckMessage, healthyCheckWarning);
		} catch (GeneratorProjectNotExistentException e) {
			LOG.warn("Configuration project not found!", e);
			healthyCheckMessage = firstStep + "NOT FOUND!\n"
					+ "=> Please import the configuration project into your workspace as stated in the "
					+ "documentation of CobiGen or in the one of your project.";
			PlatformUIUtil.openErrorDialog(healthyCheckMessage, null);
		} catch (InvalidConfigurationException e) {
			// Won't be reached anymore
			healthyCheckMessage = firstStep + "OK.";
			healthyCheckMessage += secondStep + "INVALID!";
			if (generatorConfProj != null && generatorConfProj.getLocationURI() != null) {
				Path configurationProject = Paths.get(generatorConfProj.getLocationURI());
				ContextConfigurationVersion currentVersion = new ContextConfigurationUpgrader()
						.resolveLatestCompatibleSchemaVersion(configurationProject);
				if (currentVersion != null) {
					// upgrade possible
					healthyCheckMessage += "\n\nAutomatic upgrade of the context configuration available.\n"
							+ "Detected: " + currentVersion + " / Currently Supported: "
							+ ContextConfigurationVersion.getLatest();
					healthyCheckMessage = MessageUtil.enrichMsgIfMultiError(healthyCheckMessage, report);
					report = openErrorDialogWithContextUpgrade(healthyCheckMessage, configurationProject,
							BackupPolicy.ENFORCE_BACKUP);
					if (!report.containsError(RuntimeException.class)) {
						// re-run Health Check
						Display.getCurrent().asyncExec(new Runnable() {
							@Override
							public void run() {
								execute();
							}
						});
					}
					return;
				} else {
					healthyCheckMessage += "\n\nNo automatic upgrade of the context configuration possible. "
							+ "Maybe just a mistake in the context configuration?";
					healthyCheckMessage += "\n\n=> " + e.getLocalizedMessage();
					healthyCheckMessage = MessageUtil.enrichMsgIfMultiError(healthyCheckMessage, report);
				}
			}
			PlatformUIUtil.openErrorDialog(healthyCheckMessage, null);
			LOG.warn(healthyCheckMessage, e);
		} catch (Throwable e) {
			healthyCheckMessage = "An unexpected error occurred!";
			if (report != null && healthyCheckMessage != null) {
				healthyCheckMessage = MessageUtil.enrichMsgIfMultiError(healthyCheckMessage, report);
			}
			PlatformUIUtil.openErrorDialog(healthyCheckMessage, e);
			LOG.error(healthyCheckMessage, e);
		}
	}

	/**
	 * Open up an error dialog, which encompasses the ability to upgrade the context
	 * configuration.
	 * 
	 * @param healthyCheckMessage message to be shown to the user
	 * @param configurationFolder path of the configuration folder to perform the
	 *                            upgrade
	 * @param backupPolicy        the {@link BackupPolicy} that should be used for
	 *                            the function
	 * @return <code>true</code> if the upgrade has been triggered,
	 *         <code>false</code> if the dialog has been aborted
	 */
	private HealthCheckReport openErrorDialogWithContextUpgrade(String healthyCheckMessage, Path configurationFolder,
			BackupPolicy backupPolicy) {
		MessageDialog dialog = new MessageDialog(Display.getDefault().getActiveShell(), HealthCheckDialogs.DIALOG_TITLE,
				null, healthyCheckMessage, MessageDialog.ERROR,
				new String[] { "Upgrade Context Configuration", "Abort" }, 1);
		dialog.setBlockOnOpen(true);
		int result = dialog.open();
		if (result == 0) {
			HealthCheckReport report = healthCheck.upgradeContextConfiguration(configurationFolder, backupPolicy);
			if (report.containsError(BackupFailedException.class)) {
				MessageDialog d = new MessageDialog(Display.getDefault().getActiveShell(),
						HealthCheckDialogs.DIALOG_TITLE, null,
						"Backup failed while trying to upgrade the context configuration. \n Continue without Backup?",
						MessageDialog.ERROR, new String[] { "Upgrade Without Backup", "Abort" }, 1);
				dialog.setBlockOnOpen(true);

				int newResult = d.open();
				if (newResult == 0) {
					HealthCheckReport newReport = healthCheck.upgradeContextConfiguration(configurationFolder,
							BackupPolicy.NO_BACKUP);
					return newReport;
				}
			}
			return report;
		}
		return report;
	}

	/**
	 * Open up a success dialog with the given message, which provides the ability
	 * to perform an advanced health check in addition.
	 * 
	 * @param healthyCheckMessage message to be shown to the user
	 * @param warn                if the message box should be displayed as a
	 *                            warning
	 */
	private void openSuccessDialog(String healthyCheckMessage, boolean warn) {
		MessageDialog dialog = new MessageDialog(Display.getDefault().getActiveShell(), HealthCheckDialogs.DIALOG_TITLE,
				null, healthyCheckMessage, warn ? MessageDialog.WARNING : MessageDialog.INFORMATION,
				new String[] { "Advanced Health Check", "OK" }, 1);
		dialog.setBlockOnOpen(true);

		int result = dialog.open();
		if (result == 0) {
			try {
				IPath ws = ResourcesPluginUtil.getWorkspaceLocation();
				String healthProj = ws.toPortableString() + "/" + ResourceConstants.CONFIG_PROJECT_NAME;
				File healthcheckFile = new File(healthProj);
				if (ResourcesPluginUtil.getGeneratorConfigurationProject() != null) {
					report = healthCheck.perform(healthcheckFile.toPath());
				}
				AdvancedHealthCheckDialog advancedHealthCheckDialog = new AdvancedHealthCheckDialog(report,
						healthCheck);
				advancedHealthCheckDialog.setBlockOnOpen(false);
				advancedHealthCheckDialog.open();
			} catch (GeneratorProjectNotExistentException e) {
				LOG.warn("Configuration project not found!", e);
				String s = "=> Please import the configuration project into your workspace as stated in the "
						+ "documentation of CobiGen or in the one of your project.";
				PlatformUIUtil.openErrorDialog(s, e);
			} catch (CoreException e) {
				LOG.error("An eclipse internal exception occurred while retrieving the configuration folder resource.",
						e);
				String s = "An eclipse internal exception occurred while retrieving the configuration folder resource.";
				PlatformUIUtil.openErrorDialog(s, e);
			}
		}
	}
}
