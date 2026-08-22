import 'package:calendar/view/ViewHome.dart';
import 'package:calendar/view/auth/ViewForgotPassword.dart';
import 'package:calendar/view/auth/ViewLogin.dart';
import 'package:calendar/view/auth/ViewSignup.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: "Calendar",
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      initialRoute: "/login",
      routes: {
        "/": (context) => const ViewHome(),
        "/login": (context) => const ViewLogin(),
        "/signup": (context) => const ViewSignup(),
        "/forgot-password": (context) => const ViewForgotPassword(),
      },
    );
  }
}
