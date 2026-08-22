import 'package:calendar/widget/WidgetOAuthButton.dart';
import 'package:flutter/material.dart';

class WidgetOAuth extends StatefulWidget {
  final String text;

  const WidgetOAuth(
    this.text, {
    super.key,
  });

  @override
  _WidgetOAuth createState() => _WidgetOAuth();
}

class _WidgetOAuth extends State<WidgetOAuth> {
  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(child: Divider()),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 12),
              child: Text(
                widget.text,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
            Expanded(child: Divider()),
          ],
        ),
        SizedBox(height: 30),
        Row(
          children: [
            Expanded(
              child: WidgetOAuthButton(
                icon: Icons.g_mobiledata,
                text: "Google",
              ),
            ),
            SizedBox(width: 12),
            Expanded(
              child: WidgetOAuthButton(
                icon: Icons.code,
                text: "GitHub",
              ),
            ),
          ],
        ),
        SizedBox(height: 25),
      ],
    );
  }
}