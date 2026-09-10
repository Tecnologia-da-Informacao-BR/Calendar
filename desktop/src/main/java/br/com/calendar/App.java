package br.com.calendar;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        SceneManager.init(primaryStage);
        SceneManager.navigate("/signup"); // troque pra "/login" quando ela existir
        
        primaryStage.setTitle("Project A - Agenda Mensal");
        primaryStage.setWidth(960);
        primaryStage.setHeight(768);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(640);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}