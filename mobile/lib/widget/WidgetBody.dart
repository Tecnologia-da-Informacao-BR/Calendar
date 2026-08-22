import 'package:flutter/material.dart';

class WidgetBody extends StatefulWidget {
  const WidgetBody({
    super.key,
    this.children = const [],
  });

  final List<Widget> children;

  @override
  State<WidgetBody> createState() => _WidgetBodyState();
}

class _WidgetBodyState extends State<WidgetBody> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(30.0),
          child: Column(
            children: widget.children,
          ),
        ),
      ),
    );
  }
}