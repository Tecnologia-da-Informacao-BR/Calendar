import 'package:calendar/view/auth/ViewLogin.dart';
import 'package:calendar/widget/WidgetBody.dart';
import 'package:calendar/widget/WidgetButton.dart';
import 'package:calendar/widget/WidgetInput.dart';
import 'package:flutter/material.dart';

class ViewForgotPassword extends StatefulWidget {
  const ViewForgotPassword({super.key});

  @override
  _ViewForgotPasswordState createState() => _ViewForgotPasswordState();
}

class _ViewForgotPasswordState extends State<ViewForgotPassword> {
  final _formKey = GlobalKey<FormState>();

  String? _validateEmail(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Por favor, insira seu email.';
    }
    final emailRegex = RegExp(r'^[\w\.-]+@[\w\.-]+\.\w{2,}$');
    if (!emailRegex.hasMatch(value.trim())) {
      return 'Insira um email válido.';
    }
    return null;
  }

  void _submit() {
    if (_formKey.currentState!.validate()) {
      // TODO: send OTP
    }
  }

  @override
  Widget build(BuildContext context) {
    return WidgetBody(
      children: [
        Text(
          "Esqueceu a senha?",
          style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold),
        ),
        Text(
          "Por favor, insira seu email para redefinir sua senha.",
          style: TextStyle(fontSize: 14),
        ),
        SizedBox(height: 50),

        Form(
          key: _formKey,
          child: WidgetInput(
            label: "Email",
            validator: _validateEmail,
          ),
        ),

        WidgetButton(text: "Enviar OTP", onPressed: _submit),

        GestureDetector(
          onTap: () {
            Navigator.pushAndRemoveUntil(
              context,
              MaterialPageRoute(builder: (context) => ViewLogin()),
              (route) => false,
            );
          },
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [Text("Voltar para login")],
          ),
        ),
      ],
    );
  }
}
