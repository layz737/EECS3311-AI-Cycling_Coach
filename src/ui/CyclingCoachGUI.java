package ui;

import controller.CyclingCoachController;
import model.Ride;
import model.TrainingPlan;
import model.Workout;
import util.DurationFormatter;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Swing MVC view: all application operations go through CyclingCoachController. */
public class CyclingCoachGUI {
    private final CyclingCoachController controller;
    private final List<JButton> operationButtons = new ArrayList<>();
    private final JTextField[] profileFields = new JTextField[7];
    private final JComboBox<String> mainGoal = new JComboBox<>(new String[]{"Improve FTP", "Lose Weight"});
    private final JComboBox<Ride> analysisRide = new JComboBox<>();
    private final JComboBox<Ride> firstRide = new JComboBox<>();
    private final JComboBox<Ride> secondRide = new JComboBox<>();
    private final JLabel status = new JLabel("Ready");
    private final JLabel profileStatus = new JLabel("Enter or update your rider profile.");
    private JFrame frame;
    private JButton compareButton;
    private boolean busy;

    public CyclingCoachGUI(CyclingCoachController controller) {
        this.controller = Objects.requireNonNull(controller, "controller is required");
    }

    public static void main(String[] args) {
        new CyclingCoachGUI(new CyclingCoachController()).start();
    }

    /** Constructs and shows the single frame on the event-dispatch thread. */
    public void start() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::start);
            return;
        }
        if (frame != null) {
            frame.toFront();
            return;
        }
        frame = new JFrame("AI Cycling Training Coach");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setContentPane(createContent());
        frame.setMinimumSize(new Dimension(850, 600));
        frame.setSize(1100, 750);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        loadProfile();
    }

    private JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        CardLayout layout = new CardLayout();
        JPanel cards = new JPanel(layout);
        JPanel sidebar = new JPanel(new GridLayout(11, 1, 0, 5));
        String[] names = {"Profile", "Ride Import", "Ride Analysis", "Training Plan", "AI Chat",
                "Progress", "Weakness Analysis", "Weekly Summary", "Ride Comparison", "Calendar", "Personal Bests"};
        JPanel[] panels = {profilePanel(), importPanel(), analysisPanel(), trainingPanel(), chatPanel(),
                resultPanel("Progress", "Refresh", controller::getProgress),
                resultPanel("Weakness Analysis", "Analyze", controller::analyzeWeaknesses),
                resultPanel("Weekly Summary", "Generate Summary", controller::getWeeklySummary),
                comparisonPanel(), calendarPanel(),
                resultPanel("Personal Bests (past month)", "Refresh", controller::getPersonalBests)};
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            cards.add(panels[i], name);
            JButton navigation = new JButton(name);
            navigation.addActionListener(event -> layout.show(cards, name));
            sidebar.add(navigation);
        }
        JScrollPane navigation = new JScrollPane(sidebar);
        navigation.setPreferredSize(new Dimension(205, 0));
        root.add(navigation, BorderLayout.WEST);
        root.add(cards, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        setBusy(false);
        return root;
    }

    private JPanel profilePanel() {
        String[] labels = {"Weight (kg)", "FTP (W)", "Maximum HR (bpm)", "Resting HR (bpm)",
                "Available training days per week", "Target weight (kg)", "Target FTP (W)"};
        JPanel form = new JPanel(new GridLayout(labels.length + 1, 2, 10, 10));
        for (int i = 0; i < labels.length; i++) {
            profileFields[i] = new JTextField(24);
            JLabel label = new JLabel(labels[i]);
            label.setLabelFor(profileFields[i]);
            form.add(label);
            form.add(profileFields[i]);
        }
        JLabel goalLabel = new JLabel("Main goal");
        goalLabel.setLabelFor(mainGoal);
        form.add(goalLabel);
        form.add(mainGoal);
        Consumer<String> result = text -> profileStatus.setText("Status: " + text);
        JButton save = action("Save / Update Profile", result, () -> {
            // Capture and parse Swing inputs on the EDT before scheduling model work.
            double weight = decimal(profileFields[0], "Weight");
            int ftp = integer(profileFields[1], "FTP");
            int maxHR = integer(profileFields[2], "Maximum HR");
            int restingHR = integer(profileFields[3], "Resting HR");
            int days = integer(profileFields[4], "Available training days");
            double targetWeight = decimal(profileFields[5], "Target weight");
            int targetFTP = integer(profileFields[6], "Target FTP");
            String goal = (String) mainGoal.getSelectedItem();
            runTask(() -> {
                controller.setUpProfile(weight, ftp, maxHR, restingHR, days, targetWeight, targetFTP, goal);
                return "Rider profile saved.";
            }, result, this::populateProfile);
        });
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(form);
        body.add(controls(save));
        body.add(profileStatus);
        JPanel panel = panel("Rider Profile");
        panel.add(new JScrollPane(body), BorderLayout.CENTER);
        return panel;
    }

    private JPanel importPanel() {
        JTextField path = new JTextField(28);
        JTextArea output = output();
        Consumer<String> result = sink(output);
        JButton browse = action("Browse", result, () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Garmin activity CSV (*.csv)", "csv"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                path.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JButton load = action("Import", result, () -> {
            String value = path.getText().strip();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Choose a Garmin activities CSV file.");
            }
            Path file = Path.of(value);
            runTask(() -> "Imported " + controller.importRides(file).size() + " rides.",
                    result, this::refreshRides);
        });
        return resultCard("Ride Import", controls(new JLabel("CSV file:"), path, browse, load), output);
    }

    private JPanel analysisPanel() {
        renderRides(analysisRide);
        JTextArea output = output();
        Consumer<String> result = sink(output);
        JButton analyze = action("Analyze", result, () -> {
            Ride ride = selected(analysisRide);
            runTask(() -> controller.analyzeRide(ride), result, null);
        });
        JPanel top = new JPanel(new BorderLayout());
        top.add(controls(new JLabel("Ride:"), analysisRide, analyze), BorderLayout.NORTH);
        top.add(new JLabel("Analysis uses ride summary metrics; no split or time-series data is available."), BorderLayout.SOUTH);
        return resultCard("AI Ride Analysis", top, output);
    }

    private JPanel trainingPanel() {
        JTextArea output = output();
        Consumer<String> result = sink(output);
        JButton generate = action("Generate Weekly Plan", result,
                () -> runTask(() -> formatPlan(controller.generateTrainingPlan()), result, null));
        return resultCard("Weekly Training Plan", controls(generate), output);
    }

    private JPanel chatPanel() {
        JTextField question = new JTextField(35);
        JTextArea output = output();
        Consumer<String> result = sink(output);
        JButton ask = action("Ask", result, () -> {
            String text = question.getText().strip();
            runTask(() -> controller.askAI(text), result, null);
        });
        question.addActionListener(event -> ask.doClick());
        return resultCard("AI Chat", controls(new JLabel("Question:"), question, ask), output);
    }

    private JPanel resultPanel(String title, String buttonText, Callable<String> task) {
        JTextArea output = output();
        Consumer<String> result = sink(output);
        JButton button = action(buttonText, result, () -> runTask(task, result, null));
        return resultCard(title, controls(button), output);
    }

    private JPanel comparisonPanel() {
        renderRides(firstRide);
        renderRides(secondRide);
        JTextArea output = output();
        Consumer<String> result = sink(output);
        compareButton = action("Compare", result, () -> {
            Ride first = selected(firstRide);
            Ride second = selected(secondRide);
            if (first == second) {
                throw new IllegalArgumentException("Select two different rides.");
            }
            runTask(() -> controller.compareRides(first, second), result, null);
        });
        firstRide.addActionListener(event -> updateCompareButton());
        secondRide.addActionListener(event -> updateCompareButton());
        JPanel selectors = new JPanel(new GridLayout(3, 1, 5, 5));
        selectors.add(controls(new JLabel("First ride:"), firstRide));
        selectors.add(controls(new JLabel("Second ride:"), secondRide));
        selectors.add(controls(compareButton, new JLabel("Select two different rides to enable comparison.")));
        return resultCard("Ride Comparison", selectors, output);
    }

    private JPanel calendarPanel() {
        JTextField date = new JTextField(LocalDate.now().toString(), 12);
        JTextArea output = output();
        Consumer<String> result = sink(output);
        JButton view = action("View Week", result, () -> {
            LocalDate selectedDate = LocalDate.parse(date.getText().strip());
            runTask(() -> controller.getWorkoutCalendar(selectedDate), result, null);
        });
        return resultCard("Monday-Sunday Calendar",
                controls(new JLabel("Date (yyyy-MM-dd):"), date, view), output);
    }

    private void loadProfile() {
        runTask(() -> {
            if (controller.getProfile().isPresent()) {
                return "Rider profile ready.";
            }
            return controller.loadProfile() ? "Saved rider profile loaded."
                    : "No saved profile found. Enter your profile and save.";
        }, text -> profileStatus.setText("Status: " + text), () -> {
            populateProfile();
            refreshRides();
        });
    }

    private void populateProfile() {
        controller.getProfile().ifPresent(profile -> {
            String[] values = {Double.toString(profile.getWeight()), Integer.toString(profile.getFtp()),
                    Integer.toString(profile.getMaxHR()), Integer.toString(profile.getRestingHR()),
                    Integer.toString(profile.getAvailableTrainingDays()), Double.toString(profile.getTargetWeight()),
                    Integer.toString(profile.getTargetFTP())};
            for (int i = 0; i < values.length; i++) {
                profileFields[i].setText(values[i]);
            }
            // Recognize saved options case-insensitively; legacy free text defaults to Improve FTP.
            mainGoal.setSelectedIndex("Lose Weight".equalsIgnoreCase(profile.getMainGoal().strip()) ? 1 : 0);
        });
    }

    private void refreshRides() {
        List<Ride> rides = controller.getRideHistory();
        for (JComboBox<Ride> selector : List.of(analysisRide, firstRide, secondRide)) {
            Ride previous = (Ride) selector.getSelectedItem();
            DefaultComboBoxModel<Ride> model = new DefaultComboBoxModel<>();
            rides.forEach(model::addElement);
            selector.setModel(model);
            if (previous != null && rides.contains(previous)) {
                selector.setSelectedItem(previous);
            }
        }
        if (rides.size() > 1 && firstRide.getSelectedItem() == secondRide.getSelectedItem()) {
            secondRide.setSelectedIndex(1);
        }
        updateCompareButton();
    }

    /** One worker at a time protects the existing, unsynchronized session models. */
    private void runTask(Callable<String> task, Consumer<String> result, Runnable onSuccess) {
        if (busy) {
            return;
        }
        setBusy(true);
        status.setText("Working... Navigation remains available.");
        result.accept("Working...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try {
                    return task.call();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }

            @Override
            protected void done() {
                try {
                    result.accept(get());
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    status.setText("Ready");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    result.accept("Operation interrupted.");
                    status.setText("Operation interrupted.");
                } catch (ExecutionException exception) {
                    showError(result, exception.getCause());
                } catch (CancellationException exception) {
                    result.accept("Operation cancelled.");
                    status.setText("Operation cancelled.");
                } catch (RuntimeException exception) {
                    showError(result, exception);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private JButton action(String title, Consumer<String> result, Runnable handler) {
        JButton button = new JButton(title);
        operationButtons.add(button);
        button.addActionListener(event -> {
            if (!busy) {
                try {
                    handler.run();
                } catch (RuntimeException exception) {
                    showError(result, exception);
                }
            }
        });
        return button;
    }

    private void showError(Consumer<String> result, Throwable error) {
        String message = error instanceof InterruptedException ? "Operation interrupted."
                : error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        result.accept("Error: " + message);
        status.setText("Unable to complete operation. See the selected feature's result.");
    }

    private void setBusy(boolean value) {
        busy = value;
        operationButtons.forEach(button -> button.setEnabled(!value));
        updateCompareButton();
    }

    private void updateCompareButton() {
        if (compareButton != null) {
            compareButton.setEnabled(!busy && firstRide.getSelectedItem() != null
                    && secondRide.getSelectedItem() != null
                    && firstRide.getSelectedItem() != secondRide.getSelectedItem());
        }
    }

    private static void renderRides(JComboBox<Ride> selector) {
        selector.setPreferredSize(new Dimension(370, 28));
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        selector.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = (JLabel) renderer.getListCellRendererComponent(list, value, index, selected, focus);
            label.setText(value == null ? "No rides available - import a CSV first"
                    : String.format(Locale.ROOT, "%s | %.2f km | %s | %d W average",
                    value.getDate(), value.getDistance(), DurationFormatter.format(value.getDuration()), value.getAvgPower()));
            return label;
        });
    }

    private static Ride selected(JComboBox<Ride> selector) {
        Ride ride = (Ride) selector.getSelectedItem();
        if (ride == null) {
            throw new IllegalArgumentException("No ride selected. Import a CSV and select a ride.");
        }
        return ride;
    }

    private static String formatPlan(TrainingPlan plan) {
        StringBuilder text = new StringBuilder("Generated weekly plan - active workouts:\n\n");
        for (Workout workout : plan.getWorkouts()) {
            text.append(String.format(Locale.ROOT, "%s | %s | %s | %d W\n%s\n\n",
                    workout.getDate(), workout.getType(), DurationFormatter.format(workout.getDuration()),
                    workout.getTargetPower(), workout.getDescription()));
        }
        return text.append("Days without active workouts are rest days in the generated week.").toString();
    }

    private static JPanel panel(String title) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        return panel;
    }

    private static JPanel resultCard(String title, JPanel controls, JTextArea output) {
        JPanel panel = panel(title);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.add(controls, BorderLayout.NORTH);
        content.add(new JScrollPane(output), BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel controls(java.awt.Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        for (java.awt.Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    private static JTextArea output() {
        JTextArea area = new JTextArea(15, 45);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setMargin(new java.awt.Insets(8, 8, 8, 8));
        return area;
    }

    private static Consumer<String> sink(JTextArea area) {
        return text -> {
            area.setText(text);
            area.setCaretPosition(0);
        };
    }

    private static double decimal(JTextField field, String label) {
        try {
            return Double.parseDouble(field.getText().strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " requires a number using a decimal point.");
        }
    }

    private static int integer(JTextField field, String label) {
        try {
            return Integer.parseInt(field.getText().strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " requires a whole number.");
        }
    }
}
