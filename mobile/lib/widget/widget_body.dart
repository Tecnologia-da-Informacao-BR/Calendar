import 'package:flutter/material.dart';

class WidgetBody extends StatelessWidget {
  const WidgetBody({
    super.key,
    this.children = const [],
    this.title,
    this.bottomNavigationBar,
    this.floatingActionButton,
  });

  final List<Widget> children;
  final String? title;
  final Widget? bottomNavigationBar;
  final Widget? floatingActionButton;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF7F8FC),
      appBar: title == null ? null : AppBar(title: Text(title!)),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: children,
          ),
        ),
      ),
      bottomNavigationBar: bottomNavigationBar,
      floatingActionButton: floatingActionButton,
    );
  }
}
