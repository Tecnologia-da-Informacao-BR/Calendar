import 'package:calendar/widget/WidgetBody.dart';
import 'package:flutter/material.dart';

class ViewHome extends StatefulWidget {
  const ViewHome({super.key});
  @override
  State<ViewHome> createState() => _ViewHome();
}

class _ViewHome extends State<ViewHome> {
  @override
  Widget build(BuildContext context) {
    return WidgetBody(
      children: [
        Text("Hello world")
      ],
    );
  }
}