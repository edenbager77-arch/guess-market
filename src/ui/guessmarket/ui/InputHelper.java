package guessmarket.ui;

import java.util.Scanner;

/**
 * Wraps a Scanner with re-prompting input readers so a bad keystroke never
 * crashes the program - it just asks again with a clear message.
 */
public class InputHelper {

    private final Scanner scanner;

    public InputHelper(Scanner scanner) {
        this.scanner = scanner;
    }

    public String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    public int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            try {
                return Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input: please enter a whole number.");
            }
        }
    }

    public int readIntInRange(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value < min || value > max) {
                System.out.println("Please enter a number between " + min + " and " + max + ".");
            } else {
                return value;
            }
        }
    }

    public double readPositiveDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            try {
                double value = Double.parseDouble(line.trim());
                if (value <= 0) {
                    System.out.println("Please enter a positive number.");
                } else {
                    return value;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input: please enter a number.");
            }
        }
    }
}
