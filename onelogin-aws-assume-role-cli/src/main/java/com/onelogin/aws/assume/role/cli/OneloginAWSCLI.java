package com.onelogin.aws.assume.role.cli;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfilesConfigFileWriter;
import com.amazonaws.auth.profile.internal.Profile;
import com.amazonaws.auth.profile.internal.ProfileKeyConstants;
import com.amazonaws.profile.path.AwsProfileFileLocationProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.amazonaws.services.securitytoken.model.AssumedRoleUser;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.onelogin.saml2.authn.SamlResponse;
import com.onelogin.saml2.http.HttpRequest;
import com.onelogin.sdk.conn.Client;
import com.onelogin.sdk.model.Device;
import com.onelogin.sdk.model.MFA;
import com.onelogin.sdk.model.SAMLEndpointResponse;

public class OneloginAWSCLI {

	private static int time = 45;
	private static int loop = 1;
	private static String profileName = null;
	private static File file = null;
	private static String oneloginUsernameOrEmail = null;
	private static String appId = null;
	private static String oneloginDomain = null;
	private static String awsRegion = null;
	private static String ip = null;

	public static Boolean commandParser(final String[] commandLineArguments) {
		final CommandLineParser cmd = new DefaultParser();
		final Options options = buildOptions();
		CommandLine commandLine;
		try {
			commandLine = cmd.parse(options, commandLineArguments);
			String value;

			if (commandLine.hasOption("help")) {
				HelpFormatter hf = new HelpFormatter();
				hf.printHelp("onelogin-aws-cli.jar [options]", options);
				System.out.println("");
				return false;
			}

			if (commandLine.hasOption("time")) {
				value = commandLine.getOptionValue("time");
				if (value != null && !value.isEmpty()) {
					time = Integer.parseInt(value);
				}
				if (time < 15 ) {
					time = 15;
				}
				if (time > 60 ) {
					time = 60;
				}
			}
			if (commandLine.hasOption("loop")) {
				value = commandLine.getOptionValue("loop");
				if (value != null && !value.isEmpty()) {
					loop = Integer.parseInt(value);
				}
			}
			if (commandLine.hasOption("profile")) {
				value = commandLine.getOptionValue("profile");
				if (value != null && !value.isEmpty()) {
					profileName = value;
				} else {
					profileName = "default";
				}
			}
			if (commandLine.hasOption("file")) {
				value = commandLine.getOptionValue("file");
				if (value != null && !value.isEmpty()) {
					file = new File(value);
				}
			}

			if (commandLine.hasOption("username")) {
				value = commandLine.getOptionValue("username");
				if (value != null && !value.isEmpty()) {
					oneloginUsernameOrEmail = value;
				}
			}

			if (commandLine.hasOption("subdomain")) {
				value = commandLine.getOptionValue("subdomain");
				if (value != null && !value.isEmpty()) {
					oneloginDomain = value;
				}
			}

			if (commandLine.hasOption("appid")) {
				value = commandLine.getOptionValue("appid");
				if (value != null && !value.isEmpty()) {
					appId = value;
				}
			}

			if (commandLine.hasOption("region")) {
				value = commandLine.getOptionValue("region");
				if (value != null && !value.isEmpty()) {
					awsRegion = value;
				}
			}

			if (commandLine.hasOption("ip")) {
				value = commandLine.getOptionValue("ip");
				if (value != null && !value.isEmpty()) {
					ip = value;
				}
			}

			return true;
		}
		catch (ParseException parseException) {
			System.err.println("Encountered exception while parsing" + parseException.getMessage());
			return false;
		}
	}

	public static Options buildOptions() {
		final Options options = new Options();
		options.addOption("h", "help", false, "Show the help guide");
		options.addOption("t", "time", true, "Sleep time between iterations, in minutes  [15-60 min]");
		options.addOption("l", "loop", true, "Number of iterations");
		options.addOption("p", "profile", true, "Save temporary AWS credentials using that profile name");
		options.addOption("f", "file", true, "Set a custom path to save the AWS credentials. (if not used, default AWS path is used)");
		options.addOption("r", "region", true, "Set the AWS region.");
		options.addOption("a", "appid", true, "Set AWS App ID.");
		options.addOption("d", "subdomain", true, "Onelogin Instance Sub Domain.");
		options.addOption("u", "username", true, "Onelogin username.");
		options.addOption("i", "ip", true, "Set the IP Address to bypass MFA if the IP was whitelisted");
		
		return options;
	}

	public static void main(String[] commandLineArguments) throws Exception {

		System.out.println("\nOneLogin AWS Assume Role Tool\n");

		if(!commandParser(commandLineArguments)){
			return;
		}

		// OneLogin Java SDK Client
		Client olClient = new Client();
		olClient.getAccessToken();
		Scanner scanner = new Scanner(System.in);
		try {
			String oneloginPassword = null;
			String samlResponse;

			Map<String, String> mfaVerifyInfo = null;
			Map<String, Object> result;

			String roleArn = null;
			String principalArn = null;
			String defaultAWSRegion = Regions.DEFAULT_REGION.getName();

			for (int i = 0; i < loop; i++) {
				if (i == 0) {
					// Capture OneLogin Account Details
					System.out.print("OneLogin Username: ");
					if (oneloginUsernameOrEmail == null) {
						oneloginUsernameOrEmail = scanner.next();
					}else{
						System.out.println(oneloginUsernameOrEmail);
					}

					System.out.print("OneLogin Password: ");
					try {
						oneloginPassword = String.valueOf(System.console().readPassword());
					} catch (Exception e) {
						oneloginPassword = scanner.next();
					}
					System.out.print("AWS App ID: ");
					if (appId == null) {
						appId = scanner.next();
					}else {
							System.out.println(appId);
					}

					System.out.print("Onelogin Instance Sub Domain: ");
					if (oneloginDomain == null) {
						oneloginDomain = scanner.next();
					} else {
						System.out.println(oneloginDomain);
					}
					System.out.print("IP Address");
					if (ip == null || ip.isEmpty()) {
						ip = scanner.nextLine();
						if (ip == null || ip.isEmpty()) {
							ip = null;
						}
					} else {
						System.out.println(ip);
					}
				} else {
					TimeUnit.MINUTES.sleep(time);
				}

				result = getSamlResponse(olClient, scanner, oneloginUsernameOrEmail, oneloginPassword, appId,
						oneloginDomain, mfaVerifyInfo, ip);
				mfaVerifyInfo = (Map<String, String>) result.get("mfaVerifyInfo");
				samlResponse = (String) result.get("samlResponse");

				if (i == 0) {
					HttpRequest simulatedRequest = new HttpRequest("http://example.com");
					simulatedRequest = simulatedRequest.addParameter("SAMLResponse", samlResponse);
					SamlResponse samlResponseObj = new SamlResponse(null, simulatedRequest);
					HashMap<String, List<String>> attributes = samlResponseObj.getAttributes();
					if (!attributes.containsKey("https://aws.amazon.com/SAML/Attributes/Role")) {
						System.out.print("SAMLResponse from Identity Provider does not contain AWS Role info");
						System.exit(0);
					} else {
						String selectedRole = "";
						List<String> roleData = attributes.get("https://aws.amazon.com/SAML/Attributes/Role");
						if (roleData.size() > 1) {
							System.out.println("\nAvailable AWS Roles");
							System.out.println("-----------------------------------------------------------------------");
							for (int j = 0; j < roleData.size(); j++) {
								String[] roleInfo = roleData.get(j).split(":");
								String accountId = roleInfo[4];
								String roleName = roleInfo[5].replace("role/", "");
								System.out.println(" " + j + " | " + roleName + " (Account " + accountId + ")");
							}
							System.out.println("-----------------------------------------------------------------------");
							System.out.print("Select the desired Role [0-" + (roleData.size() - 1) + "]: ");
							Integer roleSelection = Integer.valueOf(scanner.next());
							selectedRole = roleData.get(roleSelection);
						} else if (roleData.size() == 1) {
							selectedRole = roleData.get(0);
						} else {
							System.out.print("SAMLResponse from Identity Provider does not contain available AWS Role for this user");
							System.exit(0);
						}

						if (!selectedRole.isEmpty()) {
							String[] selectedRoleData = selectedRole.split(",");
							roleArn = selectedRoleData[0];
							principalArn = selectedRoleData[1];
						}
					}
				}

				AssumeRoleWithSAMLRequest assumeRoleWithSAMLRequest = new AssumeRoleWithSAMLRequest()
						.withPrincipalArn(principalArn).withRoleArn(roleArn).withSAMLAssertion(samlResponse);

				if (i == 0) {
					// AWS REGION
					if (awsRegion == null) {
						System.out.print("AWS Region (" + defaultAWSRegion + "): ");
						awsRegion = scanner.next();
						if (awsRegion.isEmpty() || awsRegion.equals("-")) {
							awsRegion = defaultAWSRegion;
						}
					}else {
						System.out.print("AWS Region: " + awsRegion);
					}
				}

				BasicAWSCredentials awsCredentials = new BasicAWSCredentials("", "");

				AWSSecurityTokenServiceClientBuilder stsBuilder = AWSSecurityTokenServiceClientBuilder.standard();

				AWSSecurityTokenService stsClient = stsBuilder.withRegion(awsRegion)
						.withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();

				AssumeRoleWithSAMLResult assumeRoleWithSAMLResult = stsClient
						.assumeRoleWithSAML(assumeRoleWithSAMLRequest);
				Credentials stsCredentials = assumeRoleWithSAMLResult.getCredentials();
				AssumedRoleUser assumedRoleUser = assumeRoleWithSAMLResult.getAssumedRoleUser();

				if (profileName == null && file == null) {
					String action = "export";
					if (System.getProperty("os.name").toLowerCase().contains("win")) {
						action = "set";
					}
					System.out.println("\n-----------------------------------------------------------------------\n");
					System.out.println("Success!\n");
					System.out.println("Assumed Role User: " + assumedRoleUser.getArn() + "\n");
					System.out.println("Temporary AWS Credentials Granted via OneLogin\n");
					System.out.println("Copy/Paste to set these as environment variables\n");
					System.out.println("-----------------------------------------------------------------------\n");

					System.out.println(action + " AWS_SESSION_TOKEN=" + stsCredentials.getSessionToken());
					System.out.println();
					System.out.println(action + " AWS_ACCESS_KEY_ID=" + stsCredentials.getAccessKeyId());
					System.out.println();
					System.out.println(action + " AWS_SECRET_ACCESS_KEY=" + stsCredentials.getSecretAccessKey());
					System.out.println();
				} else {
					if (file == null) {
						file = AwsProfileFileLocationProvider.DEFAULT_CREDENTIALS_LOCATION_PROVIDER.getLocation();
					}
					if (profileName == null) {
						profileName = "default";
					}

					Map<String, String> properties = new HashMap<String, String>();
					properties.put(ProfileKeyConstants.AWS_ACCESS_KEY_ID, stsCredentials.getAccessKeyId());
					properties.put(ProfileKeyConstants.AWS_SECRET_ACCESS_KEY, stsCredentials.getSecretAccessKey());
					properties.put(ProfileKeyConstants.AWS_SESSION_TOKEN, stsCredentials.getSessionToken());
					properties.put(ProfileKeyConstants.REGION, awsRegion);

					ProfilesConfigFileWriter.modifyOneProfile(file, profileName, new Profile(profileName, properties, null));

					System.out.println("\n-----------------------------------------------------------------------");
					System.out.println("Success!\n");
					System.out.println("Temporary AWS Credentials Granted via OneLogin\n");
					System.out.println("Updated AWS profile '" + profileName + "' located at " + file.getAbsolutePath());
					if (loop > (i+1)) {
						System.out.println("This process will regenerate credentials " + (loop - (i+1)) + " more times.\n");
						System.out.println("Press Ctrl + C to exit");
					}
				}
			}
		} finally {
			scanner.close();
		}
	}

	public static Map<String, Object> getSamlResponse(Client olClient, Scanner scanner, String oneloginUsernameOrEmail,
			String oneloginPassword, String appId, String oneloginDomain, Map<String, String> mfaVerifyInfo, String ip)
			throws Exception {
		String otpToken, stateToken;
		Device deviceSelection;
		Long deviceId;
		String deviceIdStr = null;
		Map<String, Object> result = new HashMap<String, Object>();

		SAMLEndpointResponse samlEndpointResponse = olClient.getSAMLAssertion(oneloginUsernameOrEmail, oneloginPassword,
				appId, oneloginDomain, ip);
		String status = samlEndpointResponse.getType();
		while (status.equals("pending")) {
			TimeUnit.SECONDS.sleep(30);
			samlEndpointResponse = olClient.getSAMLAssertion(oneloginUsernameOrEmail, oneloginPassword, appId,
					oneloginDomain, ip);
			status = samlEndpointResponse.getType();
		}
		String samlResponse = null;
		if (status.equals("success")) {
			if (samlEndpointResponse.getMFA() != null) {
				MFA mfa = samlEndpointResponse.getMFA();
				List<Device> devices = mfa.getDevices();

				if (mfaVerifyInfo == null) {
					System.out.println();
					System.out.println("MFA Required");
					System.out.println("Authenticate using one of these devices:");
				} else {
					deviceIdStr = mfaVerifyInfo.get("deviceId");
					if (!checkDeviceExists(devices, Long.parseLong(deviceIdStr))) {
						System.out.println();
						System.out.println("The device selected with ID " + deviceIdStr + " is not available anymore");
						System.out.println("Those are the devices available now:");
						mfaVerifyInfo = null;
					}
				}

				if (mfaVerifyInfo == null) {

					System.out.println("-----------------------------------------------------------------------");
					Device device;
					for (int i = 0; i < devices.size(); i++) {
						device = devices.get(i);
						System.out.println(" " + i + " | " + device.getType());
					}
					System.out.println("-----------------------------------------------------------------------");
					System.out.print("\nSelect the desired MFA Device [0-" + (devices.size() - 1) + "]: ");
					deviceSelection = devices.get(Integer.valueOf(scanner.next()));
					deviceId = deviceSelection.getID();
					deviceIdStr = deviceId.toString();

					System.out.print("Enter the OTP Token for " + deviceSelection.getType() + ": ");
					otpToken = scanner.next();
					stateToken = mfa.getStateToken();
					mfaVerifyInfo = new HashMap<String, String>();
					mfaVerifyInfo.put("otpToken", otpToken);
					mfaVerifyInfo.put("stateToken", stateToken);
				} else {
					otpToken = mfaVerifyInfo.get("otpToken");
					stateToken = mfaVerifyInfo.get("stateToken");
				}
				SAMLEndpointResponse samlEndpointResponseAfterVerify = olClient.getSAMLAssertionVerifying(appId,
						deviceIdStr, stateToken, otpToken, null);
				while (olClient.getErrorDescription() != null && olClient.getErrorDescription().equals("Failed authentication with this factor")) {
					System.out.print("The OTP Token was invalid, please introduce a new one: ");
					otpToken = scanner.next();
					samlEndpointResponseAfterVerify = olClient.getSAMLAssertionVerifying(appId,
							deviceIdStr, stateToken, otpToken, null);
					mfaVerifyInfo.put("otpToken", otpToken);
				}
				samlResponse = samlEndpointResponseAfterVerify.getSAMLResponse();
			} else {
				samlResponse = samlEndpointResponse.getSAMLResponse();
			}
		}
		result.put("samlResponse", samlResponse);
		result.put("mfaVerifyInfo", mfaVerifyInfo);
		return result;
	}

	public static Map<String, Object> getSamlResponse(Client olClient, Scanner scanner, String oneloginUsernameOrEmail,
			String oneloginPassword, String appId, String oneloginDomain, Map<String, String> mfaVerifyInfo)
			throws Exception {
		return getSamlResponse(olClient, scanner, oneloginUsernameOrEmail, oneloginPassword, appId,
				oneloginDomain, mfaVerifyInfo, null);
	}

	public static Boolean checkDeviceExists(List<Device> devices, Long deviceId) {
		for (Device device : devices) {
			if (device.getID() == deviceId) {
				return true;
			}
		}
		return false;
	}

}
