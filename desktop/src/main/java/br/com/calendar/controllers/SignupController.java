package br.com.calendar.controllers;

import br.com.calendar.SceneManager;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
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
    
    @FXML private ImageView brandBackgroundImage;
    @FXML private VBox brandPanel;
    @FXML private StackPane brandDecorativeFooter;
    @FXML private HBox root;
    @FXML private StackPane formPanel;
    @FXML private VBox formContent;


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


    @FXML
    public void initialize() {

        // Form content 
        formContent.prefWidthProperty().bind(
            Bindings.min(560, Bindings.max(360, formPanel.widthProperty().subtract(100)))
        );

        formContent.maxWidthProperty().bind(formContent.prefWidthProperty());
        
        formContent.translateYProperty().bind(formPanel.heightProperty().multiply(-0.04));
        formContent.maxWidthProperty().bind(formContent.prefWidthProperty());
        
        // Screen division 
        brandPanel.prefWidthProperty().bind(root.widthProperty().multiply(0.5));
        formPanel.prefWidthProperty().bind(root.widthProperty().multiply(0.5));

        // Background Image
        brandBackgroundImage.setPreserveRatio(true);

        // Ensures that the image never extends beyond the footer
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(brandDecorativeFooter.widthProperty());
        clip.heightProperty().bind(brandDecorativeFooter.heightProperty());
        brandBackgroundImage.setClip(clip);

        Runnable updateImageCover = () -> {

        if (brandBackgroundImage.getImage() == null) {
            return;
        }

        double containerWidth = brandDecorativeFooter.getWidth();
        double containerHeight = brandDecorativeFooter.getHeight();

        double imageWidth = brandBackgroundImage.getImage().getWidth();
        double imageHeight = brandBackgroundImage.getImage().getHeight();

        if (containerWidth <= 0 ||
            containerHeight <= 0 ||
            imageWidth <= 0 ||
            imageHeight <= 0) {
            return;
        }

        double scale = Math.max(
                containerWidth / imageWidth,
                containerHeight / imageHeight
        );

        brandBackgroundImage.setFitWidth(
                imageWidth * scale
        );

        brandBackgroundImage.setFitHeight(
                imageHeight * scale
        );
    };

        brandDecorativeFooter.widthProperty().addListener(
            (obs, oldVal, newVal) -> updateImageCover.run()
        );

        brandDecorativeFooter.heightProperty().addListener(
            (obs, oldVal, newVal) -> updateImageCover.run()
        );

        updateImageCover.run();

    }

}
