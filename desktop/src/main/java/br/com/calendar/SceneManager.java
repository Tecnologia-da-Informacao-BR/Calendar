package br.com.calendar;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneManager {

    private static Stage stage;
    private static final Map<String, String> ROUTES = new HashMap<>();

    static {
        ROUTES.put("/signup", "/br/com/calendar/views/SignupView.fxml");
        // ROUTES.put("/login", "/br/com/calendar/views/LoginView.fxml");
    }

    public static void init(Stage primaryStage) {
        stage = primaryStage;
    }

    public static void navigate(String route) {
        try {
            String fxmlPath = ROUTES.get(route);
            if (fxmlPath == null) {
                throw new IllegalArgumentException("Rota não encontrada: " + route);
            }
            Parent root = FXMLLoader.load(SceneManager.class.getResource(fxmlPath));
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                SceneManager.class.getResource("/br/com/calendar/css/auth.css").toExternalForm()
            );
            stage.setScene(scene);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar tela: " + route, e);
        }
    }
}