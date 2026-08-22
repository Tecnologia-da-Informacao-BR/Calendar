import 'package:flutter_test/flutter_test.dart';

import 'package:calendar/main.dart';

void main() {
  testWidgets('Application starts successfully', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    await tester.pumpAndSettle();

    expect(find.byType(MyApp), findsOneWidget);
  });
}