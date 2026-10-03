package br.com.calendar.controllers;

import java.util.function.UnaryOperator;

import br.com.calendar.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;



public class VerifyOtpController {
    
    @FXML
    private TextField otpField;

    @FXML
    private Button verifyButton;

    @FXML
    private Label otperrorLabel;
    @FXML 
    private Label generalErrorLabel;

    @FXML
    private Hyperlink loginLink;
    @FXML 
    private Hyperlink resetPasswordLink;

    @FXML
    public void initialize() {
        // Set a TextFormatter to allow only numeric input and limit to 6 characters
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d{0,6}")) {
                return change;
            }
            return null;
        };
        otpField.setTextFormatter(new TextFormatter<>(filter));
    }

    @FXML 
    private void handleVerify(){
        //Leaving for API integration later

    }

    @FXML private void handleGoToLogin() {
        SceneManager.navigate("/login");
    }

    @FXML private void handleGoToResetPassword() {
        SceneManager.navigate("/reset-password");
    }

}
