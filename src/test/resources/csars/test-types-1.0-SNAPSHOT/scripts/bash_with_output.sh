echo "changing hostname to $customHostName..."
echo "Output from create: $OUTPUT_FROM_CREATE"
sudo hostname $customHostName
export new_hostnane=$customHostName
echo "[new_hostnane]==[$customHostName]"