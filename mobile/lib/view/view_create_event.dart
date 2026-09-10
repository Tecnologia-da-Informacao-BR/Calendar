import 'package:flutter/material.dart';

class ViewCreateEvent extends StatefulWidget {
  const ViewCreateEvent({super.key});

  @override
  State<ViewCreateEvent> createState() => _ViewNewEventState();
}

class _ViewNewEventState extends State<ViewCreateEvent> {
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _locationController = TextEditingController();

  bool _allDay = false;

  String _category = "Pessoal (Azul)";
  String _status = "Agendado";
  String _repeat = "Não se repete";
  String _reminder = "5 minutos antes";

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    _locationController.dispose();

    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FC),
      appBar: AppBar(
        title: const Text(
          "Novo Evento",
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: Colors.white,
        foregroundColor: Color(0xFF0066CC),
        elevation: 0,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildLabel("TÍTULO DO EVENTO"),
              const SizedBox(height: 6),

              _buildTextField(
                controller: _titleController,
                hintText: "Adicione um título",
              ),

              const SizedBox(height: 18),

              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildLabel("INÍCIO"),
                        const SizedBox(height: 6),
                        Row(
                          children: [
                            Expanded(
                              child: _buildDateField(hintText: "mm/dd/yyyy"),
                            ),
                            const SizedBox(width: 8),
                            Expanded(child: _buildTimeField(hintText: '--:--')),
                          ],
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(width: 12),

                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildLabel("TÉRMINO"),
                        const SizedBox(height: 6),
                        Row(
                          children: [
                            Expanded(
                              child: _buildDateField(hintText: "mm/dd/yyyy"),
                            ),
                            const SizedBox(width: 8),
                            Expanded(child: _buildTimeField(hintText: "--:--")),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 16),

              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: const Color(0xFFD9DEEA)),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.access_time_outlined,
                      size: 18,
                      color: Color(0xFF596273),
                    ),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        "Dia inteiro",
                        style: TextStyle(
                          fontSize: 13,
                          color: Color(0xFF343A46),
                        ),
                      ),
                    ),
                    Switch(
                      value: _allDay,
                      onChanged: (value) {
                        setState(() {
                          _allDay = value;
                        });
                      },
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 18),

              _buildLabel("DESCRIÇÃO"),
              const SizedBox(height: 6),

              TextField(
                controller: _descriptionController,
                maxLines: 4,
                decoration: InputDecoration(
                  hintText: "Adicione notas, links ou detalhes...",
                  hintStyle: const TextStyle(
                    fontSize: 12,
                    color: Color(0xFF9AA2B1),
                  ),
                  filled: true,
                  fillColor: Colors.white,
                  contentPadding: const EdgeInsets.all(12),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(6),
                    borderSide: const BorderSide(color: Color(0xFFD9DEEA)),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(6),
                    borderSide: const BorderSide(color: Color(0xFFD9DEEA)),
                  ),
                ),
              ),

              const SizedBox(height: 18),

              Row(
                children: [
                  Expanded(
                    child: _buildDropdown(
                      label: "CATEGORIA",
                      value: _category,
                      items: const [
                        "Pessoal (Azul)",
                        "Trabalho",
                        "Reunião",
                        "Estudo",
                      ],
                      onChanged: (value) {
                        if (value == null) return;

                        setState(() {
                          _category = value;
                        });
                      },
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildDropdown(
                      label: "STATUS",
                      value: _status,
                      items: const ["Agendado", "Concluído", "Cancelado"],
                      onChanged: (value) {
                        if (value == null) return;

                        setState(() {
                          _status = value;
                        });
                      },
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 18),

              _buildLabel("LOCAL"),
              const SizedBox(height: 6),

              TextField(
                controller: _locationController,
                decoration: InputDecoration(
                  hintText: "Onde?",
                  hintStyle: const TextStyle(
                    fontSize: 12,
                    color: Color(0xFF9AA2B1),
                  ),
                  prefixIcon: const Icon(Icons.location_on_outlined, size: 18),
                  filled: true,
                  fillColor: Colors.white,
                  border: _border(),
                  enabledBorder: _border(),
                  focusedBorder: _focusedBorder(),
                ),
              ),

              const SizedBox(height: 18),

              Row(
                children: [
                  Expanded(
                    child: _buildDropdown(
                      label: "REPETIR",
                      value: _repeat,
                      items: const [
                        "Não se repete",
                        "Todos os dias",
                        "Toda semana",
                        "Todo mês",
                      ],
                      onChanged: (value) {
                        if (value == null) return;

                        setState(() {
                          _repeat = value;
                        });
                      },
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildDropdown(
                      label: "LEMBRETE",
                      value: _reminder,
                      items: const [
                        "5 minutos antes",
                        "10 minutos antes",
                        "30 minutos antes",
                        "1 hora antes",
                      ],
                      onChanged: (value) {
                        if (value == null) return;

                        setState(() {
                          _reminder = value;
                        });
                      },
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 28),

              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () {
                      Navigator.pop(context);
                    },
                    child: const Text(
                      "Cancelar",
                      style: TextStyle(color: Color(0xFF667085)),
                    ),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton.icon(
                    onPressed: _saveEvent,
                    icon: const Icon(Icons.save_outlined, size: 17),
                    label: const Text("Salvar Evento"),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF0066CC),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 13,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(6),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLabel(String text) {
    return Text(
      text,
      style: const TextStyle(
        fontSize: 9,
        fontWeight: FontWeight.w600,
        color: Color(0xFF5F6878),
        letterSpacing: 0.4,
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String hintText,
  }) {
    return TextField(
      controller: controller,
      decoration: InputDecoration(
        hintText: hintText,
        hintStyle: const TextStyle(fontSize: 12, color: Color(0xFF9AA2B1)),
        filled: true,
        fillColor: Colors.white,
        border: _border(),
        enabledBorder: _border(),
        focusedBorder: _focusedBorder(),
      ),
    );
  }

  Widget _buildDateField({required String hintText}) {
    return TextField(
      readOnly: true,
      decoration: InputDecoration(
        hintText: hintText,
        hintStyle: const TextStyle(fontSize: 11, color: Color(0xFF9AA2B1)),
        filled: true,
        fillColor: Colors.white,
        border: _border(),
        enabledBorder: _border(),
        focusedBorder: _focusedBorder(),
      ),
      onTap: () async {
        await showDatePicker(
          context: context,
          firstDate: DateTime(2020),
          lastDate: DateTime(2100),
          initialDate: DateTime.now(),
        );
      },
    );
  }

  Widget _buildTimeField({required String hintText}) {
    return TextField(
      readOnly: true,
      decoration: InputDecoration(
        hintText: hintText,
        hintStyle: const TextStyle(fontSize: 11, color: Color(0xFF9AA2B1)),
        filled: true,
        fillColor: Colors.white,
        border: _border(),
        enabledBorder: _border(),
        focusedBorder: _focusedBorder(),
      ),
      onTap: () async {
        await showTimePicker(context: context, initialTime: TimeOfDay.now());
      },
    );
  }

  Widget _buildDropdown({
    required String label,
    required String value,
    required List<String> items,
    required ValueChanged<String?> onChanged,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildLabel(label),
        const SizedBox(height: 6),
        DropdownButtonFormField<String>(
          initialValue: value,
          items: items.map((item) {
            return DropdownMenuItem(
              value: item,
              child: Text(
                item,
                style: const TextStyle(fontSize: 11, color: Color(0xFF343A46)),
              ),
            );
          }).toList(),
          onChanged: onChanged,
          decoration: InputDecoration(
            filled: true,
            fillColor: Colors.white,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 10,
              vertical: 4,
            ),
            border: _border(),
            enabledBorder: _border(),
            focusedBorder: _focusedBorder(),
          ),
          icon: const Icon(Icons.keyboard_arrow_down, size: 18),
        ),
      ],
    );
  }

  OutlineInputBorder _border() {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(6),
      borderSide: const BorderSide(color: Color(0xFFD9DEEA)),
    );
  }

  OutlineInputBorder _focusedBorder() {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(6),
      borderSide: const BorderSide(color: Color(0xFF0066CC), width: 1.2),
    );
  }

  void _saveEvent() {
    if (_titleController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Informe o título do evento.")),
      );
      return;
    }

    Navigator.pop(context);
  }
}
