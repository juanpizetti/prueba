package ar.escusub.controlips

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ar.escusub.controlips.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val esAdmin: Boolean,
    private val onClick: (Dispositivo) -> Unit,
    private val onToggle: (Dispositivo) -> Unit,
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    var items: List<Dispositivo> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    class VH(val b: ItemDeviceBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = items[position]
        val ctx = h.itemView.context
        val color = ContextCompat.getColor(ctx, if (d.activo) R.color.estado_activo else R.color.estado_pendiente)
        val bg = ContextCompat.getColor(ctx, if (d.activo) R.color.estado_activo_bg else R.color.estado_pendiente_bg)

        with(h.b) {
            card.setCardBackgroundColor(bg)
            card.strokeColor = color
            stripe.setBackgroundColor(color)
            tvEstado.setBackgroundColor(color)
            tvEstado.text = if (d.activo) "ACTIVO" else "PENDIENTE"
            tvNombre.text = d.nombreEquipo
            tvIp.text = "IP:  ${d.direccionIp}"
            tvMac.text = "MAC: ${d.direccionMac}"
            tvInfo.text = "${d.oficina} · ${d.correoInterno} · ${d.tipo}"
            tvMeta.text = buildString {
                append("Cargó: ${d.tecnicoNombre ?: "—"} · ${d.fechaRegistro ?: ""}")
                if (d.activo && d.activadoPorNombre != null) append("\nActivó: ${d.activadoPorNombre} · ${d.fechaActivacion ?: ""}")
            }

            if (esAdmin) {
                btnToggle.visibility = View.VISIBLE
                btnToggle.text = if (d.activo) "Desactivar" else "Activar"
                btnToggle.setBackgroundColor(
                    ContextCompat.getColor(ctx, if (d.activo) R.color.estado_pendiente else R.color.estado_activo)
                )
                btnToggle.setOnClickListener { onToggle(d) }
            } else {
                btnToggle.visibility = View.GONE
            }
            root.setOnClickListener { onClick(d) }
        }
    }
}
