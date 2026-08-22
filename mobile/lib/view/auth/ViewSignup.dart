import 'package:calendar/view/auth/ViewLogin.dart';
import 'package:calendar/widget/WidgetBody.dart';
import 'package:calendar/widget/WidgetButton.dart';
import 'package:calendar/widget/WidgetInput.dart';
import 'package:calendar/widget/WidgetOAuth.dart';
import 'package:flutter/material.dart';

class ViewSignup extends StatefulWidget {
  const ViewSignup({super.key});
  @override
  _ViewSignup createState() => _ViewSignup();
}

bool bool_terms = false;

class _ViewSignup extends State<ViewSignup> {
  @override
  Widget build(BuildContext context) {
    return WidgetBody(
      children: [
        Text(
          "Junte-se ao movimento",
          style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold),
        ),
        Text(
          "Crie seu centro de produtividade profissional hoje mesmo.",
          style: TextStyle(fontSize: 14),
        ),
        SizedBox(height: 50),

        WidgetInput(
          label: "Digite seu nome"
        ),
        
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
        
            WidgetInput(
              label: "Digite seu email"
            ),

            WidgetInput(
              label: "Digite sua senha",
              password: true,
              icon: Icons.lock_outline,
            ),
            
            WidgetInput(
              label: "Digite sua senha novamente",
              password: true,
              icon: Icons.lock_outline,
            ),

            SizedBox(height: 20),
            GestureDetector(
              onTap: () {
                setState(() {
                  bool_terms = !bool_terms;
                });
              },
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Checkbox(
                    value: bool_terms,
                    onChanged: (bool? value) {
                      setState(() {
                        bool_terms = !bool_terms;
                      });
                    },
                  ),
                  Expanded(
                    child: RichText(
                      text: TextSpan(
                        children: [
                          TextSpan(
                            text: "Eu concordo com os ",
                            style: TextStyle(
                              color: Colors.black,
                              fontSize: 16,
                            ),
                          ),
                          TextSpan(
                            text: "Termos e Condições",
                            style: TextStyle(
                              color: Colors.blue.shade900,
                              fontSize: 16,
                            ),
                          ),
                          TextSpan(
                            text: " e com a Política de Privacidade.",
                            style: TextStyle(
                              color: Colors.black,
                              fontSize: 16,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(height: 10),
            
            WidgetButton(text: "Criar conta"),

            WidgetOAuth("OU REGISTRE-SE COM"),
            
            GestureDetector(
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => ViewLogin(),
                  ),
                );
              },
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text("Já tem uma conta? Entrar"),
                ],
              ),
            ),
            SizedBox(height: 15),
            Center(
              child: Text(
                "© 2026 Project A. Gestão profissional de horários.",
              ),
            ),
          ],
        ),
      ]
    );
  }
}