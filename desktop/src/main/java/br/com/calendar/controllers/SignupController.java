package br.com.calendar.controllers;

import br.com.calendar.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;



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
    @FXML private StackPane brandDecorativeFooter;

    @FXML
    public void initialize() {
        brandDecorativeFooter.prefHeightProperty().bind(
            brandPanel.heightProperty().multiply(0.32)
        );
        
        brandBackgroundImage.setPreserveRatio(true);

        // Bind the width of the brand background image to the width of the brand panel
        brandBackgroundImage.fitWidthProperty().bind(brandPanel.widthProperty());
        // Height is bound to the height of the brand panel to maintain aspect ratio
        brandBackgroundImage.fitHeightProperty().bind(brandPanel.heightProperty());

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(brandDecorativeFooter.widthProperty());
        clip.heightProperty().bind(brandDecorativeFooter.heightProperty());
        brandBackgroundImage.setClip(clip);

        // Recalculete the image size when the brandDecorativeFooter is resized
        Runnable updateImageCover = () -> {
            double containerWidth = brandDecorativeFooter.getWidth();
            double containerHeight = brandDecorativeFooter.getHeight();
            double imageWidth = brandBackgroundImage.getImage().getWidth();
            double imageHeight = brandBackgroundImage.getImage().getHeight();

            if (containerWidth <= 0 || containerHeight <=0 || imageWidth <= 0 || imageHeight <= 0) {
                return; // Avoid division by zero
            }

            double scale = Math.max(containerWidth / imageWidth, containerHeight / imageHeight);

            brandBackgroundImage.setFitWidth(imageWidth * scale);
            brandBackgroundImage.setFitHeight(imageHeight * scale);
        };
        
        brandDecorativeFooter.widthProperty().addListener((obs, oldVal, newVal) -> updateImageCover.run());
        brandDecorativeFooter.heightProperty().addListener((obs, oldVal, newVal) -> updateImageCover.run());
    }

}
