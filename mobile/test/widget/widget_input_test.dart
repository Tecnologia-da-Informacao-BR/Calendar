import 'package:calendar/widget/WidgetInput.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _wrap(Widget child) {
  return MaterialApp(home: Scaffold(body: child));
}

void main() {
  group('WidgetInput', () {
    testWidgets('renders label text', (tester) async {
      await tester.pumpWidget(
        _wrap(
          Form(
            child: WidgetInput(label: 'Email'),
          ),
        ),
      );

      expect(find.text('Email'), findsOneWidget);
    });

    testWidgets('renders without validator when not provided', (tester) async {
      await tester.pumpWidget(
        _wrap(
          Form(
            child: WidgetInput(label: 'Email'),
          ),
        ),
      );

      expect(find.byType(TextFormField), findsOneWidget);
    });

    testWidgets('shows error message when validator returns a string',
        (tester) async {
      final formKey = GlobalKey<FormState>();

      await tester.pumpWidget(
        _wrap(
          Form(
            key: formKey,
            child: WidgetInput(
              label: 'Email',
              validator: (_) => 'Erro de validação',
            ),
          ),
        ),
      );

      formKey.currentState!.validate();
      await tester.pump();

      expect(find.text('Erro de validação'), findsOneWidget);
    });

    testWidgets('shows no error when validator returns null', (tester) async {
      final formKey = GlobalKey<FormState>();

      await tester.pumpWidget(
        _wrap(
          Form(
            key: formKey,
            child: WidgetInput(
              label: 'Email',
              validator: (_) => null,
            ),
          ),
        ),
      );

      formKey.currentState!.validate();
      await tester.pump();

      expect(find.byType(ErrorWidget), findsNothing);
    });

    testWidgets('obscures text when password is true', (tester) async {
      await tester.pumpWidget(
        _wrap(
          Form(
            child: WidgetInput(label: 'Senha', password: true),
          ),
        ),
      );

      final field = tester.widget<EditableText>(find.byType(EditableText));
      expect(field.obscureText, isTrue);
    });

    testWidgets('does not obscure text when password is false', (tester) async {
      await tester.pumpWidget(
        _wrap(
          Form(
            child: WidgetInput(label: 'Email', password: false),
          ),
        ),
      );

      final field = tester.widget<EditableText>(find.byType(EditableText));
      expect(field.obscureText, isFalse);
    });
  });
}
