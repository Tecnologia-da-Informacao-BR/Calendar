package br.com.calendar.controllers;

import br.com.calendar.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;



public class SignupController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField passwordConfirmationField;
    @FXML private CheckBox termsCheckBox;

    @FXML private Label nameErrorLabel;
    @FXML private Label emailErrorLabel;
    @FXML private Label passwordErrorLabel;
    @FXML private Label passwordConfirmationErrorLabel;
    @FXML private Label generalErrorLabel;

    @FXML private Hyperlink termsLink;
    @FXML private Hyperlink privacyLink;
    @FXML private Hyperlink loginLink;
    
    @FXML
    private void handleSignup(){

        //Leaving for API integration later

    }

    @FXML
    private void handleGoogleSignup(){

        //Leaving for API integration later

    }

    @FXML
    private void handleGithubSignup(){

        //Leaving for API integration later

    }

    @FXML
    private void handleGoToLogin(){

        // It's not going to work until the login view is created
        SceneManager.navigate("/login");
    }

    @FXML private ImageView brandBackgroundImage;
    @FXML private VBox brandPanel;

    @FXML
    public void initialize() {
        // Bind the width of the brand background image to the width of the brand panel
        brandBackgroundImage.fitWidthProperty().bind(brandPanel.widthProperty());
    }

}
