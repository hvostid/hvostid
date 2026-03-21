import { useParams } from 'react-router-dom';

// TODO: Implement in T32
export default function PassportFormPage() {
  const { id } = useParams();
  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Pet passport</h1>
      <p className="text-gray-500">Passport form for listing #{id}. Coming in T32.</p>
    </div>
  );
}
