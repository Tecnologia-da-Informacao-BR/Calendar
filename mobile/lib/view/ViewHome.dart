import 'package:calendar/widget/WidgetBody.dart';
import 'package:flutter/material.dart';

class ViewHome extends StatefulWidget {
  const ViewHome({super.key});

  @override
  State<ViewHome> createState() => _ViewHome();
}

class _ViewHome extends State<ViewHome> {
  int selectedIndex = 0;

  @override
  Widget build(BuildContext context) {
    return WidgetBody(
      floatingActionButton: FloatingActionButton(
        backgroundColor: Color(0xFFE6F0FF),
        onPressed: () {
          // Depois abriremos a tela de Novo Evento.
        },
        child: const Icon(Icons.add),
      ),

      bottomNavigationBar: NavigationBar(
        backgroundColor: Colors.white,
        indicatorColor: const Color(0xFFE6F0FF),
        selectedIndex: selectedIndex,
        onDestinationSelected: (index) {
          setState(() {
            selectedIndex = index;
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: "Início",
          ),
          NavigationDestination(
            icon: Icon(Icons.calendar_month_outlined),
            selectedIcon: Icon(Icons.calendar_month),
            label: "Calendário",
          ),
          NavigationDestination(
            icon: Icon(Icons.history_outlined),
            selectedIcon: Icon(Icons.history),
            label: "Histórico",
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: "Configurações",
          ),
        ],
      ),
      children: [
        _buildHeader(context),

        const SizedBox(height: 24),

        _buildSummary(context),

        const SizedBox(height: 30),

        _buildSectionTitle(),

        const SizedBox(height: 12),

        _buildActivity(
          context,
          title: "Reunião com a equipe",
          time: "Hoje • 10:00",
          category: "Trabalho",
          icon: Icons.groups_outlined,
        ),

        const SizedBox(height: 12),

        _buildActivity(
          context,
          title: "Consulta médica",
          time: "Hoje • 15:30",
          category: "Saúde",
          icon: Icons.medical_services_outlined,
        ),

        const SizedBox(height: 30),

        _buildCalendar(context),

        const SizedBox(height: 24),

        _buildInsight(context),

        const SizedBox(height: 80),
      ],
    );
  }

  // ----------------------------------------------------------
  // HEADER
  // ----------------------------------------------------------

  Widget _buildHeader(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                "Olá, Usuário!",
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                "Organize seu dia",
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(color: Colors.black),
              ),
            ],
          ),
        ),
        IconButton(
          onPressed: () {},
          icon: const Icon(Icons.notifications_none),
        ),
      ],
    );
  }

  // ----------------------------------------------------------
  // RESUMO
  // ----------------------------------------------------------

  Widget _buildSummary(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _buildSummaryCard(
            context,
            title: "HOJE",
            value: "2",
            description: "atividades",
            icon: Icons.today_outlined,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _buildSummaryCard(
            context,
            title: "SEMANA",
            value: "12",
            description: "atividades",
            icon: Icons.date_range_outlined,
          ),
        ),
      ],
    );
  }

  Widget _buildSummaryCard(
    BuildContext context, {
    required String title,
    required String value,
    required String description,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: Colors.grey,
                ),
              ),
              Icon(icon, size: 18),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            value,
            style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
          ),
          Text(
            description,
            style: const TextStyle(fontSize: 11, color: Colors.grey),
          ),
        ],
      ),
    );
  }

  // ----------------------------------------------------------
  // ATIVIDADES
  // ----------------------------------------------------------

  Widget _buildSectionTitle() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        const Text(
          "Próximas atividades",
          style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold),
        ),
        TextButton(onPressed: () {}, child: const Text("Ver tudo")),
      ],
    );
  }

  Widget _buildActivity(
    BuildContext context, {
    required String title,
    required String time,
    required String category,
    required IconData icon,
  }) {
    const primaryColor = Color(0xFF0066D6);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: primaryColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: primaryColor),
          ),

          const SizedBox(width: 14),

          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  time,
                  style: const TextStyle(color: Colors.grey, fontSize: 12),
                ),
              ],
            ),
          ),

          Container(
            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
            decoration: BoxDecoration(
              color: primaryColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              category,
              style: TextStyle(
                color: primaryColor,
                fontSize: 10,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ----------------------------------------------------------
  // CALENDÁRIO
  // ----------------------------------------------------------

  Widget _buildCalendar(BuildContext context) {
    const primaryColor = Color(0xFF0066D6);

    const days = ["D", "S", "T", "Q", "Q", "S", "S"];

    const dates = ["22", "23", "24", "25", "26", "27", "28"];

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                "Outubro 2026",
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              Row(
                children: [
                  IconButton(
                    onPressed: () {},
                    icon: const Icon(Icons.chevron_left),
                  ),
                  IconButton(
                    onPressed: () {},
                    icon: const Icon(Icons.chevron_right),
                  ),
                ],
              ),
            ],
          ),

          const SizedBox(height: 10),

          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: List.generate(days.length, (index) {
              return SizedBox(
                width: 30,
                child: Center(
                  child: Text(
                    days[index],
                    style: const TextStyle(color: Colors.grey, fontSize: 12),
                  ),
                ),
              );
            }),
          ),

          const SizedBox(height: 10),

          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: List.generate(dates.length, (index) {
              final selected = index == 2;

              return Container(
                width: 30,
                height: 30,
                decoration: BoxDecoration(
                  color: selected ? primaryColor : Colors.transparent,
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Text(
                    dates[index],
                    style: TextStyle(
                      color: selected ? Colors.white : Colors.black87,
                      fontWeight: selected
                          ? FontWeight.bold
                          : FontWeight.normal,
                    ),
                  ),
                ),
              );
            }),
          ),
        ],
      ),
    );
  }

  // ----------------------------------------------------------
  // INSIGHT
  // ----------------------------------------------------------

  Widget _buildInsight(BuildContext context) {
    const primaryColor = Color(0xFF0066D6);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: primaryColor.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.lightbulb_outline, color: primaryColor),
              const SizedBox(width: 8),
              const Text(
                "Insight do dia",
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
              ),
            ],
          ),

          const SizedBox(height: 10),

          const Text(
            "Mantenha sua agenda organizada e reserve um tempo para as tarefas mais importantes do seu dia.",
            style: TextStyle(color: Colors.grey, fontSize: 13, height: 1.4),
          ),
        ],
      ),
    );
  }
}
