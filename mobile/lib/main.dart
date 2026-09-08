import 'package:calendar/view/view_home.dart';
import 'package:calendar/view/auth/view_forgot_password.dart';
import 'package:calendar/view/auth/view_login.dart';
import 'package:calendar/view/auth/view_signup.dart';
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
      initialRoute: "/",
      routes: {
        "/": (context) => const ViewHome(),
        "/login": (context) => const ViewLogin(),
        "/signup": (context) => const ViewSignup(),
        "/forgot-password": (context) => const ViewForgotPassword(),
      },
    );
  }
}
