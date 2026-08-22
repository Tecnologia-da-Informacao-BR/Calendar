import 'package:flutter/material.dart';

class WidgetInput extends StatefulWidget {
    const WidgetInput({
      super.key,
      this.label="",
      this.password=false,
      this.icon = Icons.mail_outlined,
      this.validator,
    });

    final String label;
    final bool password;
    final IconData icon;
    final String? Function(String?)? validator;

    @override
    _WidgetInput createState() => _WidgetInput();
}

class _WidgetInput extends State<WidgetInput> {
    @override
    Widget build(BuildContext context) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextFormField(
              obscureText: widget.password,
              validator: widget.validator,
              decoration: InputDecoration(
                hintText: widget.password?"•••••••":"",
                labelText: widget.label,
                prefixIcon: Icon(widget.icon),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular((10)),
                ),
              ),
            ),
            SizedBox(height: 30),
          ]
        );
    }
}
