package io.zefio.core.common.util;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for generating cryptographically secure random identifiers,
 * passwords, and timestamp-based keys. It ensures complexity requirements
 * by mixing lowercase, uppercase, digits, and special characters.
 */
public class PasswordGenerator {

	private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
	private static final String UPPERCASE = LOWERCASE.toUpperCase();
	private static final String DIGITS = "0123456789";
	private static final String SPECIAL_CHARS = "!@#$%^&*()_+=-";

	public static String generateId(int length) {
		List<Character> idChars = new ArrayList<>();
		idChars.add(LOWERCASE.charAt(new SecureRandom().nextInt(LOWERCASE.length())));
		idChars.add(UPPERCASE.charAt(new SecureRandom().nextInt(UPPERCASE.length())));
		idChars.add(DIGITS.charAt(new SecureRandom().nextInt(DIGITS.length())));

		while (idChars.size() < length) {
			int randomIndex = new SecureRandom().nextInt(3);
			idChars.add(getRandomChar(randomIndex));
		}

		Collections.shuffle(idChars);
		return idChars.stream().map(String::valueOf).collect(Collectors.joining());
	}

	public static String generatePassword(int length) {
		List<Character> passwordChars = new ArrayList<>();

		passwordChars.add(getRandomChar(0));
		passwordChars.add(getRandomChar(1));
		passwordChars.add(getRandomChar(2));
		passwordChars.add(getRandomChar(3));

		while (passwordChars.size() < length - 4) {
			int randomIndex = new SecureRandom().nextInt(4);
			passwordChars.add(getRandomChar(randomIndex));
		}

		Collections.shuffle(passwordChars);
		return passwordChars.stream().map(String::valueOf).collect(Collectors.joining());
	}

	private static char getRandomChar(int index) {
		switch (index) {
			case 0: return LOWERCASE.charAt(new SecureRandom().nextInt(LOWERCASE.length()));
			case 1: return UPPERCASE.charAt(new SecureRandom().nextInt(UPPERCASE.length()));
			case 2: return DIGITS.charAt(new SecureRandom().nextInt(DIGITS.length()));
			case 3: return SPECIAL_CHARS.charAt(new SecureRandom().nextInt(SPECIAL_CHARS.length()));
			default: throw new IllegalArgumentException("Invalid random character index requested.");
		}
	}

	public static String generateTimeKey(String prefix){
		long timestamp = System.currentTimeMillis();
		int randomNum = new Random().nextInt(1000);
		return prefix + "-" + timestamp + "-" + randomNum;
	}

	public static void main(String[] args) {
		System.out.println("Generated ID: " + generateId(16));
		System.out.println("Generated Password: " + generatePassword(32));
	}
}
