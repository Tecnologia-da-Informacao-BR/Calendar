import 'package:calendar/view/auth/ViewForgotPassword.dart';
import 'package:calendar/view/auth/ViewSignup.dart';
import 'package:calendar/widget/WidgetBody.dart';
import 'package:calendar/widget/WidgetButton.dart';
import 'package:calendar/widget/WidgetInput.dart';
import 'package:calendar/widget/WidgetOAuth.dart';
import 'package:flutter/material.dart';

class ViewLogin extends StatefulWidget {
  const ViewLogin({super.key});
  @override
  _ViewLogin createState() => _ViewLogin();
}

class _ViewLogin extends State<ViewLogin> {
  @override
  Widget build(BuildContext context) {
    return WidgetBody(
      children: [
        Text(
          "Bem-vindo de volta",
          style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold),
        ),
        Text(
          "Por favor, insira seus dados para entrar.",
          style: TextStyle(fontSize: 14),
        ),
        SizedBox(height: 50),

        WidgetInput(label: "Email"),

        Column(
          children: [
            WidgetInput(
              label: "Senha",
              password: true,
              icon: Icons.lock_outline,
            ),

            WidgetButton(text: "Entrar"),

            GestureDetector(
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => ViewForgotPassword()),
                );
              },
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [Text("Esqueceu a senha?")],
              ),
            ),

            SizedBox(height:30),
            
            WidgetOAuth("OU ENTRAR COM"),

            GestureDetector(
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => ViewSignup()),
                );
              },
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [Text("Não tem uma conta? Criar Conta")],
              ),
            ),
            SizedBox(height: 15),
            Text("© 2026 Project A. Gestão profissional de horários."),
          ],
        ),
      ],
    );
  }
}
