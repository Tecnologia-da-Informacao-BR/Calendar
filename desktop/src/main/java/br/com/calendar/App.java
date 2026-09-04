package br.com.calendar;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        SceneManager.init(primaryStage);
        SceneManager.navigate("/signup"); // troque pra "/login" quando ela existir
        
        primaryStage.setTitle("Project A - Agenda Mensal");
        primaryStage.setWidth(1280);
        primaryStage.setHeight(720);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(650);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}