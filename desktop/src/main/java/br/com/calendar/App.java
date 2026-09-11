package br.com.calendar;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        SceneManager.init(primaryStage);
        SceneManager.navigate("/signup");
        
        primaryStage.setTitle("Project A - Agenda Mensal");
        primaryStage.setWidth(1100);
        primaryStage.setHeight(860);
        primaryStage.setMinWidth(950);
        primaryStage.setMinHeight(700);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}