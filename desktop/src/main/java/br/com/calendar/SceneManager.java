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
    private record RouteConfig(String fxmlPath, String stylesheetPath) {}

    private static final Map<String, RouteConfig> ROUTES = new HashMap<>();

    static {
        ROUTES.put("/signup", new RouteConfig(
            "/br/com/calendar/views/SignupView.fxml",
            "/br/com/calendar/css/auth.css"
        ));

        ROUTES.put("/login", new RouteConfig(
            "/br/com/calendar/views/LoginView.fxml",
            "/br/com/calendar/css/auth.css"
        ));
            
        
    }

    public static void init(Stage primaryStage) {
        stage = primaryStage;
    }

    public static void navigate(String route) {
        try {
            RouteConfig config = ROUTES.get(route);
            if (config == null) {
                throw new IllegalArgumentException("Rota não encontrada: " + route);
            }

            Parent root = FXMLLoader.load(SceneManager.class.getResource(config.fxmlPath()));
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                SceneManager.class.getResource(config.stylesheetPath()).toExternalForm()
            );
            stage.setScene(scene);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar tela: " + route, e);
        }
    }
}