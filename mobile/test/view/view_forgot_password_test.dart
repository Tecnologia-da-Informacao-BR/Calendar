import 'package:calendar/view/auth/ViewForgotPassword.dart';
import 'package:calendar/widget/WidgetButton.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _wrap() {
  return MaterialApp(
    routes: {
      '/login': (_) => const Scaffold(body: Text('Login')),
    },
    home: const ViewForgotPassword(),
  );
}

void main() {
  group('ViewForgotPassword', () {
    testWidgets('renders title and subtitle', (tester) async {
      await tester.pumpWidget(_wrap());

      expect(find.text('Esqueceu a senha?'), findsOneWidget);
      expect(
        find.text('Por favor, insira seu email para redefinir sua senha.'),
        findsOneWidget,
      );
    });

    testWidgets('renders email field', (tester) async {
      await tester.pumpWidget(_wrap());

      expect(find.byType(TextFormField), findsOneWidget);
    });

    testWidgets('renders "Enviar OTP" button', (tester) async {
      await tester.pumpWidget(_wrap());

      expect(find.widgetWithText(WidgetButton, 'Enviar OTP'), findsOneWidget);
    });

    testWidgets('renders back to login link', (tester) async {
      await tester.pumpWidget(_wrap());

      expect(find.text('Voltar para login'), findsOneWidget);
    });

    testWidgets('shows required error when submitting empty email',
        (tester) async {
      await tester.pumpWidget(_wrap());

      await tester.tap(find.widgetWithText(ElevatedButton, 'Enviar OTP'));
      await tester.pump();

      expect(find.text('Por favor, insira seu email.'), findsOneWidget);
    });

    testWidgets('shows format error when submitting invalid email',
        (tester) async {
      await tester.pumpWidget(_wrap());

      await tester.enterText(find.byType(TextFormField), 'email-invalido');
      await tester.tap(find.widgetWithText(ElevatedButton, 'Enviar OTP'));
      await tester.pump();

      expect(find.text('Insira um email válido.'), findsOneWidget);
    });

    testWidgets('shows no error when submitting valid email', (tester) async {
      await tester.pumpWidget(_wrap());

      await tester.enterText(
          find.byType(TextFormField), 'usuario@email.com');
      await tester.tap(find.widgetWithText(ElevatedButton, 'Enviar OTP'));
      await tester.pump();

      expect(find.text('Por favor, insira seu email.'), findsNothing);
      expect(find.text('Insira um email válido.'), findsNothing);
    });

    testWidgets('does not show error before first submit', (tester) async {
      await tester.pumpWidget(_wrap());

      expect(find.text('Por favor, insira seu email.'), findsNothing);
      expect(find.text('Insira um email válido.'), findsNothing);
    });
  });
}
