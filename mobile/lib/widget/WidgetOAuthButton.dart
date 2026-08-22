import 'package:flutter/material.dart';

class WidgetOAuthButton extends StatefulWidget {
    const WidgetOAuthButton({
      super.key,
      this.text = "",
      this.icon = Icons.g_mobiledata
    });

    final String text;
    final IconData icon;

    @override
    _WidgetOAuthButton createState() => _WidgetOAuthButton();
}

class _WidgetOAuthButton extends State<WidgetOAuthButton> {
    @override
    Widget build(BuildContext context) {
        return OutlinedButton.icon(
          onPressed: () {},
          icon: Icon(widget.icon),
          label: Text(widget.text),
          style: OutlinedButton.styleFrom(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
        );
    }
}
